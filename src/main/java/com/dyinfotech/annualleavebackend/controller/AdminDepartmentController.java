package com.dyinfotech.annualleavebackend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.dto.DepartmentDto;
import com.dyinfotech.annualleavebackend.service.AuthService;
import com.dyinfotech.annualleavebackend.service.DepartmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "관리자 전용 - 부서 관리", description = "부서 조회/등록/수정 API (대표이사 전용)")
@RestController
@RequestMapping("/api/admin/departments")
@RequiredArgsConstructor
public class AdminDepartmentController {

    private final AuthService authService;
    private final DepartmentService departmentService;

    @Operation(summary = "부서 전체 조회", description = "전체 부서를 조회한다. (소프트 딜리트된 부서 제외)")
    @GetMapping
    public ResponseEntity<List<DepartmentDto.DepartmentResponse>> getDepartments(@AuthenticationPrincipal EmployeePrincipal principal) {
    	authService.checkPersonnelAuthority(principal.employeeId());
    	return ResponseEntity.ok(departmentService.findAllForAdmin().stream()
    														.map(DepartmentDto.DepartmentResponse::from)
    														.toList());
    }

    @Operation(summary = "부서 등록", description = "새로운 부서를 등록한다. 중복된 부서명은 409를 반환한다.")
    @PostMapping
    public ResponseEntity<DepartmentDto.CreateResponse> createDepartment(@AuthenticationPrincipal EmployeePrincipal principal, 
    																	@Valid @RequestBody DepartmentDto.CreateRequest request) {
    	authService.checkPersonnelAuthority(principal.employeeId());
    	Long departmentId = departmentService.createDepartment(request.getDepartmentName());
    	return ResponseEntity.ok(DepartmentDto.CreateResponse.builder()
    														.departmentId(departmentId)
    														.build());
    }

    @Operation(summary = "부서 수정", description = "부서명을 변경한다. 대표이사 부서는 변경할 수 없다.")
    @PutMapping("/{departmentId}")
    public ResponseEntity<Void> updateDepartment(@AuthenticationPrincipal EmployeePrincipal principal, 
    											@PathVariable("departmentId") Long departmentId, 
    											@Valid @RequestBody DepartmentDto.UpdateRequest request) {
    	authService.checkPersonnelAuthority(principal.employeeId());
    	departmentService.renameDepartment(departmentId, request.getDepartmentName());
    	return ResponseEntity.ok().build();
    }

}
