package com.dyinfotech.annualleavebackend.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.common.security.RequireAdminOrPersonnelAuthority;
import com.dyinfotech.annualleavebackend.dto.FcmTokenDto;
import com.dyinfotech.annualleavebackend.dto.RegisterCommonDto;
import com.dyinfotech.annualleavebackend.dto.RegisterDto;
import com.dyinfotech.annualleavebackend.service.AuthService;
import com.dyinfotech.annualleavebackend.service.RefreshTokenCookieService;
import com.dyinfotech.annualleavebackend.service.RefreshTokenService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "관리자 전용 - 인증 관리", description = "사원 등록 등 권한 부여 API")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final RefreshTokenService refreshTokenService;
    
    @Operation(summary = "FCM 토큰 등록", description = "로그인 시 FCM 토큰 발급에 의한 병목때문에 별도로 처리한다.")
    @PostMapping("/sync-fcm-token")
    public CompletableFuture<ResponseEntity<Void>> syncFcmToken(
            HttpServletRequest servletRequest,
            @AuthenticationPrincipal EmployeePrincipal principal,
            @RequestHeader(value = "X-SSO-Session-Marker", required = false)
            String authSessionMarker,
            @Valid @RequestBody FcmTokenDto.FcmTokenRequest request) {
        String refreshToken = refreshTokenCookieService.read(servletRequest);
        if (refreshToken != null) {
            if (authSessionMarker == null || authSessionMarker.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "FCM 등록 세션 식별자가 없습니다.");
            }

            RefreshTokenService.CurrentSessionIdentity identity =
                    refreshTokenService.currentSessionIdentity(refreshToken);
            if (!java.util.Objects.equals(identity.employeeId(), principal.employeeId())
                    || !java.util.Objects.equals(
                            identity.sessionMarker(),
                            authSessionMarker)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "현재 로그인 세션과 FCM 등록 세션이 일치하지 않습니다.");
            }
        } else if (authSessionMarker != null && !authSessionMarker.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "FCM 등록 세션을 확인할 refresh cookie가 없습니다.");
        }

    	return authService.syncFcmToken(
                            principal.employeeId(),
                            request,
                            authSessionMarker)
    						.thenApply(v -> ResponseEntity.ok().build());
    }

    @Operation(summary = "부서, 팀, 직급 조회", description = "신규 사원 등록 시 로그인한 관리자가 부여 가능한 부서, 팀, 직급을 조회한다.")
    @GetMapping("/common")
    @RequireAdminOrPersonnelAuthority
    public ResponseEntity<RegisterCommonDto.RegisterCommonResponse> getCommonData(@AuthenticationPrincipal EmployeePrincipal principal) {
    	return ResponseEntity.ok(authService.getCommonData(principal.employeeId()));
    }

    @Operation(summary = "사원 등록", description = "관리자가 신규 사원의 로그인 계정 정보를 등록한다.")
    @PostMapping("/register")
    @RequireAdminOrPersonnelAuthority
    public ResponseEntity<RegisterDto.RegisterResponse> signUp(@AuthenticationPrincipal EmployeePrincipal principal, @Valid @RequestBody RegisterDto.RegisterRequest request) {
    	return ResponseEntity.ok(authService.registerEmployee(principal.employeeId(), request));
    }

}