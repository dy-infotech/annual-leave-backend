package com.dyinfotech.annualleavebackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyinfotech.annualleavebackend.dto.RegisterCommonDto;
import com.dyinfotech.annualleavebackend.dto.RegisterDto;
import com.dyinfotech.annualleavebackend.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;
    
    @GetMapping("/common")
//	public ResponseEntity<RegisterCommonDto.RegisterCommonResponse> getCommonData(@Valid @RequestBody RegisterCommonDto.RegisterCommonRequest request) {
//		return ResponseEntity.ok(authService.getCommonData(request));
    public ResponseEntity<RegisterCommonDto.RegisterCommonResponse> getCommonData() {
    	return ResponseEntity.ok(authService.getCommonData());
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterDto.RegisterResponse> signUp(@AuthenticationPrincipal Long employeeId, @Valid @RequestBody RegisterDto.RegisterRequest request) {
        return ResponseEntity.ok(authService.registerEmployee(employeeId, request));
    }

}