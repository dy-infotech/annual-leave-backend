package com.dyinfotech.annualleavebackend.controller;


import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/me")
    public EmployeeDto.EmployResponse getMyInfo(@AuthenticationPrincipal Long employeeId) {
        return employeeService.getMyInfo(employeeId);
    }
}
