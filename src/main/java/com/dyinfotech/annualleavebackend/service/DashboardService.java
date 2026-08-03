package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
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
    private final TeamService teamService;
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
        DashboardDto.LeaveRequestSummaryResponse allEmployeeSummary = employeeLeaveService.createSingleRoleResolver(employeeId).isAdmin() ? getAllEmployeeRequestSummary(employee) : null;

        return DashboardDto.builder()
                .myLeaveInfoResponse(myLeaveInfo)
                .myRequestSummary(myRequestSummary)
                .allEmployeeRequestSummary(allEmployeeSummary)
                .build();
    }

    private DashboardDto.MyLeaveInfoResponse getMyLeaveInfo(Employee employee, float currTotalLeaveDays) {
        float usedDays = leaveRequestRepository.sumApprovedUseDays(employee.getEmployeeId(), clock);
        float remainingLeaveDays = commonService.getRemainingDays(employee, currTotalLeaveDays, usedDays);

        return DashboardDto.MyLeaveInfoResponse.builder()
                .totalLeaveDays(usedDays + remainingLeaveDays)		// usedDays + remainingLeaveDays - currTotalLeaveDays = adjustedLeaveDays
                .usedLeaveDays(usedDays)
                .remainingLeaveDays(remainingLeaveDays)
                .build();
    }

    private DashboardDto.LeaveRequestSummaryResponse getMyRequestSummary(Long employeeId) {
    	Map<LeaveRequestStatus, Long> countMap = leaveRequestRepository.countByStatus(employeeId, clock).stream()
																			    	                  .collect(Collectors.toMap(
																			    	                      LeaveRequestStatusCount::status,
																			    	                      LeaveRequestStatusCount::count
																			    	                  ));
        return DashboardDto.LeaveRequestSummaryResponse.builder()
                .pendingCount(countMap.getOrDefault(LeaveRequestStatus.PENDING, 0L))
                .approvedCount(countMap.getOrDefault(LeaveRequestStatus.APPROVED, 0L))
                .rejectedCount(countMap.getOrDefault(LeaveRequestStatus.REJECTED, 0L))
                .build();
    }

    private DashboardDto.LeaveRequestSummaryResponse getAllEmployeeRequestSummary(Employee employee) {
    	Set<String> directTeams = new HashSet<>();
    	Set<String> accessibleTeams = new HashSet<>();
    	for (Team team : employee.getTeams()) {
    		String myTeam = team.getTeam();
    		directTeams.add(myTeam);
    		accessibleTeams.addAll(teamService.getSelfAndDescendants(myTeam));
    	}
    	
    	Map<LeaveRequestStatus, Long> countMap = leaveRequestRepository.countByStatus(directTeams, accessibleTeams, clock)
    																	.stream()
    																	.collect(Collectors.toMap(
    																		LeaveRequestStatusCount::status,
    																		LeaveRequestStatusCount::count
    																	));

        return DashboardDto.LeaveRequestSummaryResponse.builder()
                .pendingCount(countMap.getOrDefault(LeaveRequestStatus.PENDING, 0L))
                .approvedCount(countMap.getOrDefault(LeaveRequestStatus.APPROVED, 0L))
                .rejectedCount(countMap.getOrDefault(LeaveRequestStatus.REJECTED, 0L))
                .build();
    }
}
