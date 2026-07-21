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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<SignUpDto.SignUpResponse> signUp(@Valid @RequestBody SignUpDto.SignUpRequest request) {
        return ResponseEntity.ok(authService.signUp(request));
    }

    @PostMapping("/signin")
    public ResponseEntity<SignInDto.SignInResponse> signIn(@Valid @RequestBody SignInDto.SignInRequest request) {
        return ResponseEntity.ok(authService.signIn(request));
    }
    
    // 💡 주소가 다르게 매핑되어 있다면 아래와 같이 플러터 요청 주소와 일치시킵니다.    
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordDto.Request request) {
        authService.forgotPassword(request); // 서비스 메서드명도 맞추면 알아보기 쉽습니다.
        return ResponseEntity.ok().build();   // 성공 시 200 OK 반환 (앱의 response.statusCode != 200 조건 만족)
    } 
    
    // 💡 주소가 다르게 매핑되어 있다면 아래와 같이 플러터 요청 주소와 일치시킵니다.    
    @PostMapping("/find-id")
    public ResponseEntity<ForgotPasswordDto.FindIdResponse> findId(@Valid @RequestBody ForgotPasswordDto.FindIdRequest request) {
        // 1. 서비스 메서드를 호출하여 찾은 아이디 결과(DTO)를 받아옵니다.
        ForgotPasswordDto.FindIdResponse response = authService.findId(request);
        
        // 2. 결과 데이터를 body에 담아서 200 OK로 반환합니다.
        return ResponseEntity.ok(response);
    }

    
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long employeeId,
            							@RequestBody(required = false) LogoutDto.LogoutRequest request) {
        authService.logout(employeeId, request == null ? null : request.getFcmToken());
        return ResponseEntity.ok().build();
    }
}