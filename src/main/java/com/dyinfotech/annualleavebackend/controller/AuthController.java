package com.dyinfotech.annualleavebackend.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.dto.FindDataDto;
import com.dyinfotech.annualleavebackend.dto.FindDataDto.EmailResponse;
import com.dyinfotech.annualleavebackend.dto.LogoutDto;
import com.dyinfotech.annualleavebackend.dto.SignInDto;
import com.dyinfotech.annualleavebackend.dto.SignUpDto;
import com.dyinfotech.annualleavebackend.service.AuthService;
import com.dyinfotech.annualleavebackend.service.PasswordResetService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@Tag(name = "전체 사용자 인증 관리", description = "사용자의 로그인, 사용 등록, 계정 찾기 등 신원 확인 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @Operation(summary = "사용 등록", description = "관리자가 등록한 계정 정보를 이용하여 사용 등록(회원 가입)을 한다.")
    @PostMapping("/signup")
    public ResponseEntity<SignUpDto.SignUpResponse> signUp(@Valid @RequestBody SignUpDto.SignUpRequest request) {
        return ResponseEntity.ok(authService.signUp(request));
    }

    @Operation(summary = "로그인", description = "사용 등록 이후에 등록된 계정 정보로 로그인 가능하다.")
    @PostMapping("/signin")
    public ResponseEntity<SignInDto.SignInResponse> signIn(@Valid @RequestBody SignInDto.SignInRequest request) {
        return ResponseEntity.ok(authService.signIn(request));
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

    @Operation(summary = "아이디 찾기", description = "성함, 이메일을 입력하면 등록된 이메일로 아이디가 발송된다.")
    @PostMapping("/find-id")
    public ResponseEntity<Void> findId(@Valid @RequestBody FindDataDto.FindIdRequest request) {
        authService.findId(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "로그아웃", description = "FCM 토큰을 폐기한다.(DB에서 삭제, FCM 서버에서 토픽 해제)")
    @PostMapping("/logout")
    public CompletableFuture<ResponseEntity<Void>> logout(@AuthenticationPrincipal EmployeePrincipal principal,
            							@RequestBody(required = false) LogoutDto.LogoutRequest request) {
        return authService.logout(principal.employeeId(), request == null ? null : request.getFcmToken())
                .thenApply(v -> ResponseEntity.ok().build());
    }
}