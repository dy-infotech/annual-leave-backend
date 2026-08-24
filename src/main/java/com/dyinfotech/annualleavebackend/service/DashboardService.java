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
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.TeamManager;
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
    private final EmployeeService employeeService;
    private final EmployeeLeaveService employeeLeaveService;

    private final Clock clock;
    
    public DashboardDto getDashboard(Long employeeId, Role role) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        // 현재 연도 연차일수 계산 및 설정
        float currYearLeaveDays = employeeLeaveService.getCalculatedCurrYearLeaveDays(employee);
        if (employee.getCurrTotalLeaveDays() != currYearLeaveDays) {
        	employeeService.updateCurrTotalLeaveDays(employee.getEmployeeId(), currYearLeaveDays);
        }

        // 1. 내 휴가 정보
        DashboardDto.MyLeaveInfoResponse myLeaveInfo = getMyLeaveInfo(employee, currYearLeaveDays);

        // 2. 내 휴가 요청 요약
        DashboardDto.LeaveRequestSummaryResponse myRequestSummary = getMyRequestSummary(employeeId);

        // 3. 관리자일 경우, 전직원 요약 포함
        DashboardDto.LeaveRequestSummaryResponse allEmployeeSummary = employeeLeaveService.createAuthorityResolver(employeeId).isAdmin(employeeId) ? getAllEmployeeRequestSummary(employee) : null;

        return DashboardDto.builder()
                .myLeaveInfoResponse(myLeaveInfo)
                .myRequestSummary(myRequestSummary)
                .allEmployeeRequestSummary(allEmployeeSummary)
                .build();
    }

    private DashboardDto.MyLeaveInfoResponse getMyLeaveInfo(Employee employee, float currTotalLeaveDays) {
        float usedDays = leaveRequestRepository.sumRequestedUseDays(employee.getEmployeeId(), clock);
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
    	Long excludeId = employee.getEmployeeId();
    	Set<String> directTeams = new HashSet<>();
    	Set<TeamManager> accessibleTeams = new HashSet<>();
    	for (TeamManager team : employee.getTeams()) {
    		String myTeam = team.getTeam().getTeamName();
    		directTeams.add(myTeam);
    		if (myTeam.equals(team.getParentTeam().getTeamName())) {
    			excludeId = null;	// 최상위 팀이면 제외할 필요 없음 (스스로 승인이 가능하므로)
    		}
    		accessibleTeams.addAll(teamService.getSelfAndDescendants(myTeam));
    	}
    	
    	Map<LeaveRequestStatus, Long> countMap = leaveRequestRepository.countByStatus(excludeId, directTeams, accessibleTeams, clock)
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
