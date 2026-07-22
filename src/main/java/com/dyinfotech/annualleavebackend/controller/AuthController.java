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
      
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordDto.Request request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/find-id")
    public ResponseEntity<Void> findId(@Valid @RequestBody ForgotPasswordDto.FindIdRequest request) {
        authService.findId(request);
        return ResponseEntity.ok().build();
    }

    
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long employeeId,
            							@RequestBody(required = false) LogoutDto.LogoutRequest request) {
        authService.logout(employeeId, request == null ? null : request.getFcmToken());
        return ResponseEntity.ok().build();
    }
}