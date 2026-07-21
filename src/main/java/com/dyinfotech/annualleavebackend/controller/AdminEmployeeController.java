package com.dyinfotech.annualleavebackend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.service.EmployeeService;

import lombok.RequiredArgsConstructor;

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
