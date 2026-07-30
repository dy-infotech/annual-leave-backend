package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.dto.DashboardDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;
import com.dyinfotech.annualleavebackend.repository.projection.LeaveRequestStatusCount;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final CommonService commonService;
    private final EmployeeLeaveService employeeLeaveService;

    private final Clock clock;
    
    public DashboardDto getDashboard(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        // 현재 연도 연차일수 계산
        float currYearLeaveDays = employeeLeaveService.getCalculatedCurrYearLeaveDays(employee);

        // 1. 내 휴가 정보
        DashboardDto.MyLeaveInfoResponse myLeaveInfo = getMyLeaveInfo(employee, currYearLeaveDays);

        // 2. 내 휴가 요청 요약
        DashboardDto.LeaveRequestSummaryResponse myRequestSummary = getMyRequestSummary(employeeId);

        // 3. 관리자일 경우, 전직원 요약 포함
        DashboardDto.LeaveRequestSummaryResponse allEmployeeSummary = employeeLeaveService.resolveRole(employeeId) == Role.ADMIN ? getAllEmployeeRequestSummary(employee) : null;

        return DashboardDto.builder()
                .myLeaveInfoResponse(myLeaveInfo)
                .myRequestSummary(myRequestSummary)
                .allEmployeeRequestSummary(allEmployeeSummary)
                .build();
    }

    private DashboardDto.MyLeaveInfoResponse getMyLeaveInfo(Employee employee, float currTotalLeaveDays) {
        Float usedDays = leaveRequestRepository.sumApprovedUseDays(employee.getEmployeeId(), clock);

        return DashboardDto.MyLeaveInfoResponse.builder()
                .totalLeaveDays(currTotalLeaveDays)
                .usedLeaveDays(usedDays)
                .remainingLeaveDays(commonService.getRemainingDays(employee, currTotalLeaveDays, usedDays))
                .build();
    }

    private DashboardDto.LeaveRequestSummaryResponse getMyRequestSummary(Long employeeId) {
//		long pending = leaveRequestRepository.countByEmployee_EmployeeIdAndStatus(employeeId, LeaveRequestStatus.PENDING, clock);
//		long approved = leaveRequestRepository.countByEmployee_EmployeeIdAndStatus(employeeId, LeaveRequestStatus.APPROVED, clock);
//		long rejected = leaveRequestRepository.countByEmployee_EmployeeIdAndStatus(employeeId, LeaveRequestStatus.REJECTED, clock);
//    	
//    	return DashboardDto.LeaveRequestSummaryResponse.builder()
//    			.pendingCount(pending)
//    			.approvedCount(approved)
//    			.rejectedCount(rejected)
//    			.build();
    	Map<LeaveRequestStatus, Long> countMap = leaveRequestRepository.countByStatus(employeeId, clock).stream()
																			    	                  .collect(Collectors.toMap(
																			    	                      LeaveRequestStatusCount::getStatus,
																			    	                      LeaveRequestStatusCount::getCount
																			    	                  ));
        return DashboardDto.LeaveRequestSummaryResponse.builder()
                .pendingCount(countMap.getOrDefault(LeaveRequestStatus.PENDING, 0L))
                .approvedCount(countMap.getOrDefault(LeaveRequestStatus.APPROVED, 0L))
                .rejectedCount(countMap.getOrDefault(LeaveRequestStatus.REJECTED, 0L))
                .build();
    }

    private DashboardDto.LeaveRequestSummaryResponse getAllEmployeeRequestSummary(Employee employee) {
    	List<Team> teams = employee.getTeams();
//		long pending = teams.isEmpty() ? 0L : leaveRequestRepository.countByStatus(teams, LeaveRequestStatus.PENDING, clock);
//		long approved = teams.isEmpty() ? 0L : leaveRequestRepository.countByStatus(teams, LeaveRequestStatus.APPROVED, clock);
//		long rejected = teams.isEmpty() ? 0L : leaveRequestRepository.countByStatus(teams, LeaveRequestStatus.REJECTED, clock);
//    	
//    	return DashboardDto.LeaveRequestSummaryResponse.builder()
//    			.pendingCount(pending)
//    			.approvedCount(approved)
//    			.rejectedCount(rejected)
//    			.build();
    	Map<LeaveRequestStatus, Long> countMap = leaveRequestRepository.countByStatus(teams, clock).stream()
																					                .collect(Collectors.toMap(
																					                    LeaveRequestStatusCount::getStatus,
																					                    LeaveRequestStatusCount::getCount
																					                ));

        return DashboardDto.LeaveRequestSummaryResponse.builder()
                .pendingCount(countMap.getOrDefault(LeaveRequestStatus.PENDING, 0L))
                .approvedCount(countMap.getOrDefault(LeaveRequestStatus.APPROVED, 0L))
                .rejectedCount(countMap.getOrDefault(LeaveRequestStatus.REJECTED, 0L))
                .build();
    }
}
