package com.dyinfotech.annualleavebackend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.service.AuthService;
import com.dyinfotech.annualleavebackend.service.EmployeeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "관리자 전용 - 사원 조회", description = "사원 조회 API")
@RestController
@RequestMapping("/api/admin/employees")
@RequiredArgsConstructor
public class AdminEmployeeController {

    private final AuthService authService;
    private final EmployeeService employeeService;

    @Operation(summary = "전체 사원 조회", description = "관리자가 신규 사원 등록 시 채번된 사번을 조회한다.")
    @GetMapping("/all")
    public List<EmployeeDto.EmployeeResponse> getAllEmployees(@AuthenticationPrincipal EmployeePrincipal principal, 
    															@RequestParam(name = "searchParam", required = false) String searchParam) {
    	authService.checkAdmin(principal.employeeId());
    	return employeeService.getAllEmployees(searchParam);
    }
    
    
    @PutMapping("/{employeeNumber}") 
    public ResponseEntity<Void> updateEmployeeByAdmin(
    		@AuthenticationPrincipal EmployeePrincipal principal,
            @PathVariable("employeeNumber") String employeeNumber,
            @RequestBody EmployeeDto.EmployeeAdminUpdateRequest request) {
    	authService.checkAdmin(principal.employeeId());
        employeeService.updateEmployeeByAdmin(principal.employeeId(), employeeNumber, request);
        return ResponseEntity.ok().build();
    }
 
}
