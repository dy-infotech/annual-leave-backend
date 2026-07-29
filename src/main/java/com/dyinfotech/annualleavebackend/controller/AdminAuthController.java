package com.dyinfotech.annualleavebackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "관리자 전용 - 인증 관리", description = "사원 등록 등 권한 부여 API")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    @Operation(summary = "부서, 팀, 직급 조회", description = "신규 사원 등록 시 로그인한 관리자가 부여 가능한 부서, 팀, 직급을 조회한다.")
    @GetMapping("/common")
//	public ResponseEntity<RegisterCommonDto.RegisterCommonResponse> getCommonData(@Valid @RequestBody RegisterCommonDto.RegisterCommonRequest request) {
//		return ResponseEntity.ok(authService.getCommonData(request));
    public ResponseEntity<RegisterCommonDto.RegisterCommonResponse> getCommonData(@AuthenticationPrincipal Long employeeId) {
    	return ResponseEntity.ok(authService.getCommonData(employeeId));
    }

    @Operation(summary = "사원 등록", description = "관리자가 신규 사원의 로그인 계정 정보를 등록한다.")
    @PostMapping("/register")
    public ResponseEntity<RegisterDto.RegisterResponse> signUp(@AuthenticationPrincipal Long employeeId, @Valid @RequestBody RegisterDto.RegisterRequest request) {
        return ResponseEntity.ok(authService.registerEmployee(employeeId, request));
    }

}