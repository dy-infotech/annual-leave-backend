package com.dyinfotech.annualleavebackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.service.EmployeeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/me")
    public EmployeeDto.EmployeeResponse getMyInfo(@AuthenticationPrincipal Long employeeId) {
        return employeeService.getMyInfo(employeeId);
    }
    
    @PatchMapping("/me/modify-email")
    public ResponseEntity<Void> getMyInfo(@AuthenticationPrincipal Long employeeId, @Valid @RequestBody EmployeeDto.ModifyEmailRequest email) {
        employeeService.changeEmail(employeeId, email.getEmail());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Long employeeId, @Valid @RequestBody EmployeeDto.PasswordChangeRequest request) {
        employeeService.changePassword(employeeId, request);
        return ResponseEntity.noContent().build();
    }
}
