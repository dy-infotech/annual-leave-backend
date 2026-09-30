package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.dto.LeaveRejectDto;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

class LeaveApprovalIdempotencyRegressionTest {

    private LeaveRequestRepository leaveRequestRepository;
    private EmployeeService employeeService;
    private TeamService teamService;
    private EmployeeCacheInvalidator employeeCacheInvalidator;
    private LeaveApprovalService service;

    @BeforeEach
    void setUp() {
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        employeeService = mock(EmployeeService.class);
        teamService = mock(TeamService.class);
        employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);
        service = new LeaveApprovalService(
                leaveRequestRepository,
                employeeService,
                teamService,
                employeeCacheInvalidator,
                Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    void approve_sameCommittedResult_isSuccessfulNoOp() {
        Employee requester = employee(20L, "신청자");
        Employee manager = employee(10L, "관리자");
        LeaveRequest approved = request(100L, LeaveRequestStatus.APPROVED, manager, null);
        when(approved.getEmployee()).thenReturn(requester);
        when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(approved));
        when(employeeService.getEmployeeList(any())).thenReturn(java.util.List.of(manager, requester));
        when(teamService.resolveCurrentApproverIds(requester)).thenReturn(java.util.Set.of(10L));

        var response = service.approveLeaveRequest(100L, 10L);

        assertEquals("APPROVED", response.getStatus());
        verify(leaveRequestRepository, never()).updateLeaveRequest(any(), any(), any(), any(), any(), any());
        verify(employeeCacheInvalidator, never()).afterEmployeeViewChange(any(Long.class));
    }

    @Test
    void reject_sameCommittedResultAndReason_isSuccessfulNoOp() {
        Employee requester = employee(20L, "신청자");
        Employee manager = employee(10L, "관리자");
        LeaveRequest rejected = request(101L, LeaveRequestStatus.REJECTED, manager, "사유");
        when(rejected.getEmployee()).thenReturn(requester);
        when(leaveRequestRepository.findById(101L)).thenReturn(Optional.of(rejected));
        when(employeeService.getEmployeeList(any())).thenReturn(java.util.List.of(manager, requester));
        when(teamService.resolveCurrentApproverIds(requester)).thenReturn(java.util.Set.of(10L));

        var response = service.rejectLeaveRequest(101L, 10L, rejectRequest("사유"));

        assertEquals("REJECTED", response.getStatus());
        assertEquals("사유", response.getRejectReason());
        verify(leaveRequestRepository, never()).updateLeaveRequest(any(), any(), any(), any(), any(), any());
        verify(employeeCacheInvalidator, never()).afterEmployeeViewChange(any(Long.class));
    }

    @Test
    void approve_sameCommittedResultWithoutCurrentAuthority_isDenied() {
        Employee requester = employee(20L, "신청자");
        Employee formerManager = employee(10L, "과거 관리자");
        LeaveRequest approved = request(103L, LeaveRequestStatus.APPROVED, formerManager, null);
        when(approved.getEmployee()).thenReturn(requester);
        when(leaveRequestRepository.findById(103L)).thenReturn(Optional.of(approved));
        when(employeeService.getEmployeeList(any())).thenReturn(java.util.List.of(formerManager, requester));
        when(teamService.resolveCurrentApproverIds(requester)).thenReturn(java.util.Set.of(11L));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.approveLeaveRequest(103L, 10L));

        assertEquals(404, exception.getStatusCode().value());
        verify(leaveRequestRepository, never()).updateLeaveRequest(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reject_replayWithDifferentReason_isConflict() {
        Employee requester = employee(20L, "신청자");
        Employee manager = employee(10L, "관리자");
        LeaveRequest rejected = request(102L, LeaveRequestStatus.REJECTED, manager, "기존 사유");
        when(rejected.getEmployee()).thenReturn(requester);
        when(leaveRequestRepository.findById(102L)).thenReturn(Optional.of(rejected));
        when(employeeService.getEmployeeList(any())).thenReturn(java.util.List.of(manager, requester));
        when(teamService.resolveCurrentApproverIds(requester)).thenReturn(java.util.Set.of(10L));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.rejectLeaveRequest(102L, 10L, rejectRequest("변경 사유")));

        assertEquals(409, exception.getStatusCode().value());
    }

    private static Employee employee(Long id, String name) {
        Employee employee = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(id);
        when(employee.getName()).thenReturn(name);
        when(employee.getTeamName()).thenReturn("팀");
        when(employee.isActive(any(LocalDate.class))).thenReturn(true);
        return employee;
    }

    private static LeaveRequest request(Long id, LeaveRequestStatus status, Employee manager, String reason) {
        LeaveRequest request = mock(LeaveRequest.class);
        when(request.getRequestId()).thenReturn(id);
        when(request.getStatus()).thenReturn(status);
        when(request.getManager()).thenReturn(manager);
        when(request.getRejectReason()).thenReturn(reason);
        return request;
    }

    private static LeaveRejectDto.LeaveRejectRequest rejectRequest(String reason) {
        LeaveRejectDto.LeaveRejectRequest request = new LeaveRejectDto.LeaveRejectRequest();
        try {
            Field field = LeaveRejectDto.LeaveRejectRequest.class.getDeclaredField("rejectReason");
            field.setAccessible(true);
            field.set(request, reason);
            return request;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
