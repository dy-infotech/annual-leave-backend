package com.dyinfotech.annualleavebackend.service;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.dto.DashboardDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final CommonService commonService;
    private final EmployeeLeaveService employeeLeaveService;

    public DashboardDto getDashboard(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        // 현재 연도 연차일수 계산
        float currYearLeaveDays = employeeLeaveService.getCalculatedCurrYearLeaveDays(employee);

        // 1. 내 휴가 정보
        DashboardDto.MyLeaveInfoResponse myLeaveInfo = getMyLeaveInfo(employee, currYearLeaveDays);

        // 2. 내 휴가 요청 요약
        DashboardDto.LeaveRequestSummaryResponse myRequestSummary = getMyRequestSummary(employeeId);

        // 3. 관리자일 경우, 전직원 요약 포함
        DashboardDto.LeaveRequestSummaryResponse allEmployeeSummary = employeeLeaveService.resolveRole(employeeId) == Role.ADMIN ? getAllEmployeeRequestSummary() : null;

        return DashboardDto.builder()
                .myLeaveInfoResponse(myLeaveInfo)
                .myRequestSummary(myRequestSummary)
                .allEmployeeRequestSummary(allEmployeeSummary)
                .build();
    }

    private DashboardDto.MyLeaveInfoResponse getMyLeaveInfo(Employee employee, float currTotalLeaveDays) {
        Float usedDays = leaveRequestRepository.sumApprovedUseDays(employee.getEmployeeId());

        return DashboardDto.MyLeaveInfoResponse.builder()
                .totalLeaveDays(currTotalLeaveDays)
                .usedLeaveDays(usedDays)
                .remainingLeaveDays(commonService.getRemainingDays(employee, currTotalLeaveDays, usedDays))
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
