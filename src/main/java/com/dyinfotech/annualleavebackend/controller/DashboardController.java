package com.dyinfotech.annualleavebackend.controller;

import com.dyinfotech.annualleavebackend.common.security.LoginPrincipal;
import com.dyinfotech.annualleavebackend.dto.DashboardDto;
import com.dyinfotech.annualleavebackend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대시보드", description = "휴가 정보, 휴가 신청 현황 확인 API")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "대시보드 조회", description = "로그인한 사용자의 휴가 정보(배정, 사용, 잔여), 휴가 신청 현황을 조회한다. 관리자의 경우 전 직원 대기/승인/반려 현황을 일괄 조회한다.")
    @GetMapping
    public DashboardDto getDashboard(@AuthenticationPrincipal LoginPrincipal principal) {
        return dashboardService.getDashboard(principal.employeeId(), principal.role());
    }
}