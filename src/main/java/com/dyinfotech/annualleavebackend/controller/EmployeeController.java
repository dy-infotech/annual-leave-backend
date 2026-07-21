package com.dyinfotech.annualleavebackend.controller;

import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/me")
    public EmployeeDto.EmployeeResponse getMyInfo(@AuthenticationPrincipal Long employeeId) {
        return employeeService.getMyInfo(employeeId);
    }
    
    @GetMapping("/all")
    public List<EmployeeDto.EmployeeResponse> getAllEmployees(@RequestParam(name = "searchParam", required = false) String searchParam) {
        return employeeService.getAllEmployees(searchParam);
    }
    
    @PatchMapping("/me/modify-email")
    public ResponseEntity<Void> getMyInfo(@AuthenticationPrincipal Long employeeId, @Valid @RequestBody String email) {
        employeeService.changeEmail(employeeId, email);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Long employeeId, @Valid @RequestBody EmployeeDto.PasswordChangeRequest request) {
        employeeService.changePassword(employeeId, request);
        return ResponseEntity.noContent().build();
    }
}
