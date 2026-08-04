package com.dyinfotech.annualleavebackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.service.EmployeeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "로그인 사용자 정보 관리", description = "내 정보 조회, 이메일 변경, 비밀번호 변경 API")
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @Operation(summary = "내 정보 조회", description = "로그인과 동시에 로그인한 사용자 정보 조회한다.")
    @GetMapping("/me")
    public EmployeeDto.EmployeeResponse getMyInfo(@AuthenticationPrincipal EmployeePrincipal principal) {
        return employeeService.getMyInfo(principal.employeeId(), principal.role());
    }

    @Operation(summary = "이메일 변경", description = "로그인한 사용자가 이메일 변경을 위해 사용한다.")
    @PatchMapping("/me/email")
    public ResponseEntity<Void> changeEmail(@AuthenticationPrincipal EmployeePrincipal principal, @Valid @RequestBody EmployeeDto.ModifyEmailRequest email) {
        employeeService.changeEmail(principal.employeeId(), email.getEmail());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "비밀번호 변경", description = "로그인한 사용자가 비밀번호 변경을 위해 사용한다.")
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal EmployeePrincipal principal, @Valid @RequestBody EmployeeDto.PasswordChangeRequest request) {
        employeeService.changePassword(principal.employeeId(), request);
        return ResponseEntity.noContent().build();
    }
 
}