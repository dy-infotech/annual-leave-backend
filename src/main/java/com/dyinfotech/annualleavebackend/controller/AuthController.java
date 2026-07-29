package com.dyinfotech.annualleavebackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyinfotech.annualleavebackend.dto.ForgotPasswordDto;
import com.dyinfotech.annualleavebackend.dto.LogoutDto;
import com.dyinfotech.annualleavebackend.dto.SignInDto;
import com.dyinfotech.annualleavebackend.dto.SignUpDto;
import com.dyinfotech.annualleavebackend.service.AuthService;

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

    @Operation(summary = "비밀번호 찾기", description = "사번, 이메일을 입력하면 등록된 이메일로 임시 비밀번호가 발송된다.")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordDto.Request request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "아이디 찾기", description = "성함, 이메일을 입력하면 등록된 이메일로 아이디가 발송된다.")
    @PostMapping("/find-id")
    public ResponseEntity<Void> findId(@Valid @RequestBody ForgotPasswordDto.FindIdRequest request) {
        authService.findId(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "로그아웃", description = "FCM 토큰을 폐기한다.(DB에서 삭제, FCM 서버에서 토픽 해제)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long employeeId,
            							@RequestBody(required = false) LogoutDto.LogoutRequest request) {
        authService.logout(employeeId, request == null ? null : request.getFcmToken());
        return ResponseEntity.ok().build();
    }
}