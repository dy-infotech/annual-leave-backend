package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
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
import com.dyinfotech.annualleavebackend.dto.DashboardDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;
import com.dyinfotech.annualleavebackend.repository.projection.LeaveRequestStatusCount;

import com.dyinfotech.annualleavebackend.service.TeamService.ManagedTeam;

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
    
    public DashboardDto getDashboard(Long employeeId, Role role) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        LocalDate today = LocalDate.now(clock);
        Year currentYear = Year.from(today);
        LocalDate leaveYearStart = currentYear.atDay(1);
        LocalDate leaveYearEnd = currentYear.atMonth(Month.DECEMBER).atEndOfMonth();

        // 현재 연도 연차일수 계산
        float currYearLeaveDays = employeeLeaveService.getCalculatedCurrYearLeaveDays(employee);

        // 내 휴가 정보
        DashboardDto.MyLeaveInfoResponse myLeaveInfo = getMyLeaveInfo(employee, currYearLeaveDays, leaveYearStart, leaveYearEnd);

        // 내 휴가 요청 요약
        DashboardDto.LeaveRequestSummaryResponse myRequestSummary = getMyRequestSummary(employeeId, leaveYearStart, leaveYearEnd);

        // 관리자일 경우 요청 시점 DB 조직 상태로 관리 범위와 요약을 함께 계산한다.
        // 관리자 여부와 실제 조회 범위가 서로 다른 snapshot을 사용하지 않게 한다.
        TeamService.ManagedScope managedScope =
                teamService.findManagedScopeFromDatabase(employeeId);
        DashboardDto.LeaveRequestSummaryResponse allEmployeeSummary =
                managedScope.directTeams().isEmpty()
                        ? null
                        : getAllEmployeeRequestSummary(employee, managedScope);

        return DashboardDto.builder()
                .myLeavePeriod(DashboardDto.LeavePeriodResponse.builder()
                        .startDate(leaveYearStart)
                        .endDate(leaveYearEnd)
                        .build())
                .myLeaveInfoResponse(myLeaveInfo)
                .myRequestSummary(myRequestSummary)
                .allEmployeeRequestSummary(allEmployeeSummary)
                .build();
    }

    private DashboardDto.MyLeaveInfoResponse getMyLeaveInfo(
            Employee employee,
            float currTotalLeaveDays,
            LocalDate leaveYearStart,
            LocalDate leaveYearEnd
    ) {
        float usedDays = leaveRequestRepository.sumRequestedUseDays(
                employee.getEmployeeId(),
                List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING),
                leaveYearStart,
                leaveYearEnd
        );
        float remainingLeaveDays = commonService.getRemainingDays(employee, currTotalLeaveDays, usedDays);

        return DashboardDto.MyLeaveInfoResponse.builder()
                .totalLeaveDays(usedDays + remainingLeaveDays)		// usedDays + remainingLeaveDays - currTotalLeaveDays = adjustedLeaveDays
                .usedLeaveDays(usedDays)
                .remainingLeaveDays(remainingLeaveDays)
                .build();
    }

    private DashboardDto.LeaveRequestSummaryResponse getMyRequestSummary(Long employeeId, LocalDate startDate, LocalDate endDate) {
    	Map<LeaveRequestStatus, Long> countMap = leaveRequestRepository.countByStatus(employeeId, startDate, endDate).stream()
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

    private DashboardDto.LeaveRequestSummaryResponse getAllEmployeeRequestSummary(
            Employee employee,
            TeamService.ManagedScope managedScope) {
        Long excludeId = employee.getEmployeeId();
        List<ManagedTeam> managedTeams = managedScope.directTeams();

        Set<String> directTeams = managedTeams.stream()
                .map(ManagedTeam::teamName)
                .collect(Collectors.toSet());

        Set<ManagedTeam> accessibleTeams = managedScope.accessibleTeams();

        if (managedTeams.stream().anyMatch(team -> team.teamId().equals(team.parentTeamId()))) {
            excludeId = null;
        }

        Set<String> accessibleTeamNames = accessibleTeams.stream()
                .map(ManagedTeam::teamName)
                .collect(Collectors.toSet());

        Set<Long> childTeamProjectManagerIds = accessibleTeams.stream()
                .filter(team -> !team.teamId().equals(team.parentTeamId()))
                .filter(team -> directTeams.contains(team.parentTeamName()))
                .map(ManagedTeam::projectManagerId)
                .collect(Collectors.toSet());

        Map<LeaveRequestStatus, Long> countMap = leaveRequestRepository
                .countByStatus(excludeId, directTeams, accessibleTeamNames, childTeamProjectManagerIds, clock)
                .stream()
                .collect(Collectors.toMap(
                        LeaveRequestStatusCount::status,
                        LeaveRequestStatusCount::count));

        return DashboardDto.LeaveRequestSummaryResponse.builder()
                .pendingCount(countMap.getOrDefault(LeaveRequestStatus.PENDING, 0L))
                .approvedCount(countMap.getOrDefault(LeaveRequestStatus.APPROVED, 0L))
                .rejectedCount(countMap.getOrDefault(LeaveRequestStatus.REJECTED, 0L))
                .build();
    }
}
