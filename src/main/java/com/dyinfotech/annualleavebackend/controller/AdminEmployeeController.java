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
@RequestMapping("/api/admin/employees")
@RequiredArgsConstructor
public class AdminEmployeeController {

    private final EmployeeService employeeService;
    
    @GetMapping("/all")
    public List<EmployeeDto.EmployeeResponse> getAllEmployees(@RequestParam(name = "searchParam", required = false) String searchParam) {
        return employeeService.getAllEmployees(searchParam);
    }
    
}
