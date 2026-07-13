package com.dyinfotech.annualleavebackend.controller;

import com.dyinfotech.annualleavebackend.dto.SignInDto;
import com.dyinfotech.annualleavebackend.dto.SignUpDto;
import com.dyinfotech.annualleavebackend.dto.ForgotPasswordDto;
import com.dyinfotech.annualleavebackend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}