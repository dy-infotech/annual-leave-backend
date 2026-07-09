package com.dyinfotech.annualleavebackend.service;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.Role;
import com.dyinfotech.annualleavebackend.dto.DashboardDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public DashboardDto getDashboard(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        // 1. 내 휴가 정보
        DashboardDto.MyLeaveInfoResponse myLeaveInfo = getMyLeaveInfo(employee);

        // 2. 내 휴가 요청 요약
        DashboardDto.LeaveRequestSummaryResponse myRequestSummary = getMyRequestSummary(employeeId);

        // 3. 관리자일 경우, 전직원 요약 포함
        DashboardDto.LeaveRequestSummaryResponse allEmployeeSummary = employee.getRole() == Role.ADMIN ? getAllEmployeeRequestSummary() : null;

        return DashboardDto.builder()
                .myLeaveInfoResponse(myLeaveInfo)
                .myRequestSummary(myRequestSummary)
                .allEmployeeRequestSummary(allEmployeeSummary)
                .build();
    }

    private DashboardDto.MyLeaveInfoResponse getMyLeaveInfo(Employee employee) {
        Float usedDays = leaveRequestRepository.sumApprovedUseDays(employee.getEmployeeId());
        Float remainingDays = employee.getCurrTotalLeaveDays() - usedDays;

        return DashboardDto.MyLeaveInfoResponse.builder()
                .totalLeaveDays(employee.getCurrTotalLeaveDays())
                .usedLeaveDays(usedDays)
                .remainingLeaveDays(remainingDays)
                .build();
    }

    private DashboardDto.LeaveRequestSummaryResponse getMyRequestSummary(Long employeeId) {
        long pending = leaveRequestRepository.countByEmployee_EmployeeIdAndStatus(employeeId, LeaveRequestStatus.PENDING);
        long approved = leaveRequestRepository.countByEmployee_EmployeeIdAndStatus(employeeId, LeaveRequestStatus.APPROVED);
        long rejected = leaveRequestRepository.countByEmployee_EmployeeIdAndStatus(employeeId, LeaveRequestStatus.REJECTED);

        return DashboardDto.LeaveRequestSummaryResponse.builder()
                .pendingCount(pending)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .build();
    }

    private DashboardDto.LeaveRequestSummaryResponse getAllEmployeeRequestSummary() {
        long pending = leaveRequestRepository.countByStatus(LeaveRequestStatus.PENDING);
        long approved = leaveRequestRepository.countByStatus(LeaveRequestStatus.APPROVED);
        long rejected = leaveRequestRepository.countByStatus(LeaveRequestStatus.REJECTED);

        return DashboardDto.LeaveRequestSummaryResponse.builder()
                .pendingCount(pending)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .build();
    }
}
