package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.dto.DashboardDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;
import com.dyinfotech.annualleavebackend.repository.projection.LeaveRequestStatusCount;

class DashboardPeriodRegressionTest {

    @Test
    void dashboardUsesSameFiscalYearPeriodForLeaveInfoAndMyRequestSummary() {
        Long employeeId = 1L;
        LocalDate periodStart = LocalDate.of(2026, 1, 1);
        LocalDate periodEnd = LocalDate.of(2026, 12, 31);
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-18T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        Employee employee = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(employeeId);
        when(employee.getHireDate()).thenReturn(periodStart);

        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        LeaveRequestRepository leaveRequestRepository = mock(LeaveRequestRepository.class);
        TeamService teamService = mock(TeamService.class);
        CommonService commonService = mock(CommonService.class);
        EmployeeLeaveService employeeLeaveService = mock(EmployeeLeaveService.class);
        EmployeeLeaveService.EmployeeAuthorityResolver authorityResolver =
                mock(EmployeeLeaveService.EmployeeAuthorityResolver.class);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(employeeLeaveService.getCalculatedCurrYearLeaveDays(employee)).thenReturn(15.0f);
        when(leaveRequestRepository.sumRequestedUseDays(
                employeeId,
                List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING),
                periodStart,
                periodEnd
        )).thenReturn(8.0f);
        when(commonService.getRemainingDays(employee, 15.0f, 8.0f)).thenReturn(7.0f);
        when(leaveRequestRepository.countByStatus(employeeId, periodStart, periodEnd))
                .thenReturn(List.of(new LeaveRequestStatusCount(LeaveRequestStatus.APPROVED, 7L)));
        when(employeeLeaveService.createAuthorityResolver(employeeId)).thenReturn(authorityResolver);
        when(authorityResolver.isAdmin(employeeId)).thenReturn(false);

        DashboardService service = new DashboardService(
                employeeRepository,
                leaveRequestRepository,
                teamService,
                commonService,
                employeeLeaveService,
                clock
        );

        DashboardDto dashboard = service.getDashboard(employeeId, Role.EMPLOYEE);

        assertEquals(periodStart, dashboard.getMyLeavePeriod().getStartDate());
        assertEquals(periodEnd, dashboard.getMyLeavePeriod().getEndDate());
        assertEquals(15.0f, dashboard.getMyLeaveInfoResponse().getTotalLeaveDays(), 0.001f);
        assertEquals(8.0f, dashboard.getMyLeaveInfoResponse().getUsedLeaveDays(), 0.001f);
        assertEquals(7.0f, dashboard.getMyLeaveInfoResponse().getRemainingLeaveDays(), 0.001f);
        assertEquals(7L, dashboard.getMyRequestSummary().getApprovedCount());

        verify(leaveRequestRepository).countByStatus(employeeId, periodStart, periodEnd);
    }
}
