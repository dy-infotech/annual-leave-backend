package com.dyinfotech.annualleavebackend.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.service.EmployeeService;

import lombok.RequiredArgsConstructor;

@Tag(name = "관리자 전용 - 사원 조회", description = "사원 조회 API")
@RestController
@RequestMapping("/api/admin/employees")
@RequiredArgsConstructor
public class AdminEmployeeController {

    private final EmployeeService employeeService;

    @Operation(summary = "전체 사원 조회", description = "관리자가 신규 사원 등록 시 채번된 사번을 조회한다.")
    @GetMapping("/all")
    public List<EmployeeDto.EmployeeResponse> getAllEmployees(@RequestParam(name = "searchParam", required = false) String searchParam) {
        return employeeService.getAllEmployees(searchParam);
    }
    
}
