package com.dyinfotech.annualleavebackend.controller;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.dto.FindDataDto;
import com.dyinfotech.annualleavebackend.dto.FindDataDto.EmailResponse;
import com.dyinfotech.annualleavebackend.dto.LogoutDto;
import com.dyinfotech.annualleavebackend.dto.SignInDto;
import com.dyinfotech.annualleavebackend.dto.SignUpDto;
import com.dyinfotech.annualleavebackend.service.AuthService;
import com.dyinfotech.annualleavebackend.service.PasswordResetService;
import com.dyinfotech.annualleavebackend.service.RefreshTokenCookieService;
import com.dyinfotech.annualleavebackend.service.RefreshTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@Slf4j
@Tag(name = "전체 사용자 인증 관리", description = "사용자의 로그인, 사용 등록, 계정 찾기 등 신원 확인 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final String REFRESH_REQUEST_HEADER = "X-SSO-Refresh";
    private static final String BACKGROUND_LOGOUT_HEADER = "X-SSO-Background-Logout";
    private static final String SESSION_MARKER_HEADER = "X-SSO-Session-Marker";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieService refreshTokenCookieService;

    @Operation(summary = "사용 등록", description = "관리자가 등록한 계정 정보를 이용하여 사용 등록(회원 가입)을 한다.")
    @PostMapping("/signup")
    public ResponseEntity<SignUpDto.SignUpResponse> signUp(@Valid @RequestBody SignUpDto.SignUpRequest request) {
        return ResponseEntity.ok(authService.signUp(request));
    }

    @Operation(summary = "로그인", description = "Access JWT와 공통 HttpOnly refresh cookie session을 발급한다.")
    @PostMapping("/signin")
    public ResponseEntity<SignInDto.SignInResponse> signIn(
            @Valid @RequestBody SignInDto.SignInRequest request) {
        SignInDto.SignInResponse access = authService.signIn(request);
        RefreshTokenService.IssuedRefreshToken refresh =
                refreshTokenService.issue(access.getEmployeeId());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.issue(refresh).toString())
                .body(withSessionMarker(access, refresh.sessionMarker()));
    }

    @Operation(summary = "Access Token 갱신", description = "공통 Refresh Token Rotation으로 annual-leave Access JWT를 재발급한다.")
    @PostMapping("/refresh")
    public ResponseEntity<SignInDto.SignInResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        requireRefreshRequestHeader(request);

        String token = refreshTokenCookieService.read(request);
        if (token == null) {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clear().toString());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token이 없습니다.");
        }

        String expectedSessionMarker = request.getHeader(SESSION_MARKER_HEADER);

        try {
            RefreshTokenService.RefreshResult result =
                    refreshTokenService.rotate(token, expectedSessionMarker);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.issue(result.refresh()).toString())
                    .body(withSessionMarker(result.access(), result.refresh().sessionMarker()));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clear().toString());
            }
            throw e;
        }
    }
    
    @Operation(summary = "이름으로 이메일 찾기", description = "이름으로 이메일 리스트 조회 후에 선택해서 사번 조회 가능하다.")
    @PostMapping("/find-email-by-id")
    public ResponseEntity<EmailResponse> findEmail(@Valid @RequestBody FindDataDto.FindEmailByIdRequest request) {
        return ResponseEntity.ok(authService.findEmails(request));
    }

    @Operation(summary = "사번으로 이메일 찾기", description = "사번으로 이메일 리스트 조회 후에 선택해서 임시 비밀번호 발급 가능하다.")
    @PostMapping("/find-email-by-employee-number")
    public ResponseEntity<EmailResponse> findEmail(@Valid @RequestBody FindDataDto.FindEmailByEmployeeNumberRequest request) {
        return ResponseEntity.ok(authService.findEmails(request));
    }
    
    @Operation(summary = "비밀번호 재설정 요청", description = "사번과 이메일이 일치하면 일회용 재설정 토큰을 이메일로 발송한다.")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody FindDataDto.FindPasswordRequest request) {
        passwordResetService.requestReset(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "비밀번호 재설정 확정", description = "이메일로 받은 일회용 토큰을 검증한 뒤 새 비밀번호로 변경한다.")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody FindDataDto.ResetPasswordRequest request) {
        passwordResetService.confirmReset(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "현재 SSO 세션 식별", description = "명시 로그아웃한 세션과 이후 다른 시스템에서 생성된 새 SSO 세션을 구분한다.")
    @PostMapping("/session-marker")
    public ResponseEntity<Map<String, String>> sessionMarker(HttpServletRequest request) {
        requireRefreshRequestHeader(request);
        String token = refreshTokenCookieService.read(request);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token이 없습니다.");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("sessionMarker", refreshTokenService.currentSessionMarker(token)));
    }

    @Operation(summary = "아이디 찾기", description = "성함, 이메일을 입력하면 등록된 이메일로 아이디가 발송된다.")
    @PostMapping("/find-id")
    public ResponseEntity<Void> findId(@Valid @RequestBody FindDataDto.FindIdRequest request) {
        authService.findId(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "로그아웃", description = "공통 refresh session과 FCM 토큰을 폐기한다.")
    @PostMapping("/logout")
    public CompletableFuture<ResponseEntity<Void>> logout(
            HttpServletRequest servletRequest,
            @AuthenticationPrincipal EmployeePrincipal principal,
            @Valid @RequestBody(required = false) LogoutDto.LogoutRequest request) {
        requireRefreshRequestHeader(servletRequest);

        boolean backgroundLogout =
                "1".equals(servletRequest.getHeader(BACKGROUND_LOGOUT_HEADER));
        String refreshToken = refreshTokenCookieService.read(servletRequest);
        String expectedSessionMarker = servletRequest.getHeader(SESSION_MARKER_HEADER);

        Long refreshEmployeeId = null;
        if (refreshToken != null) {
            refreshEmployeeId = backgroundLogout
                    ? refreshTokenService.revokeIfSessionMarker(
                            refreshToken, expectedSessionMarker)
                    : refreshTokenService.revoke(refreshToken);
        }

        // background logout에서 marker가 맞지 않으면 이후 새 세션의 FCM binding도 건드리지 않는다.
        Long employeeId = refreshEmployeeId != null
                ? refreshEmployeeId
                : backgroundLogout
                        ? null
                        : principal != null ? principal.employeeId() : null;

        if (employeeId != null) {
            try {
                authService.logout(
                                employeeId,
                                request == null ? null : request.getFcmToken())
                        .whenComplete((ignored, error) -> {
                            if (error != null) {
                                log.warn(
                                        "로그아웃 후 FCM 정리에 실패했습니다. employeeId={}",
                                        employeeId,
                                        error);
                            }
                        });
            } catch (RuntimeException e) {
                // refresh session 폐기와 브라우저 로그아웃은 이미 독립적으로 처리할 수 있다.
                // FCM 정리 시작 실패가 로그아웃 응답을 지연하거나 실패시키지 않게 격리한다.
                log.warn(
                        "로그아웃 후 FCM 정리를 시작하지 못했습니다. employeeId={}",
                        employeeId,
                        e);
            }
        }

        if (backgroundLogout) {
            // 늦게 도착한 이전 세션의 logout 응답이 이후 로그인 세션의
            // refresh cookie를 삭제하지 않도록 background 요청은 cookie를 건드리지 않는다.
            return CompletableFuture.completedFuture(
                    ResponseEntity.noContent()
                            .cacheControl(CacheControl.noStore())
                            .build());
        }

        return CompletableFuture.completedFuture(
                ResponseEntity.noContent()
                        .cacheControl(CacheControl.noStore())
                        .header(
                                HttpHeaders.SET_COOKIE,
                                refreshTokenCookieService.clear().toString())
                        .build());
    }

    private SignInDto.SignInResponse withSessionMarker(
            SignInDto.SignInResponse access,
            String sessionMarker) {
        return SignInDto.SignInResponse.builder()
                .token(access.getToken())
                .employeeId(access.getEmployeeId())
                .name(access.getName())
                .role(access.getRole())
                .email(access.getEmail())
                .ssoSessionMarker(sessionMarker)
                .build();
    }

    private void requireRefreshRequestHeader(HttpServletRequest request) {
        if (!"1".equals(request.getHeader(REFRESH_REQUEST_HEADER))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "유효하지 않은 인증 갱신 요청입니다.");
        }
    }
}