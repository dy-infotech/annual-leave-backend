package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.type.LeaveType;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestListDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

class LeaveRequestRegressionTest {

    private static final Long EMPLOYEE_ID = 1L;
    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 7, 1);
    private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 1, 20);

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-01-15T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private LeaveRequestRepository leaveRequestRepository;
    private EmployeeRepository employeeRepository;
    private EmployeeLeaveService employeeLeaveService;
    private NotificationOutboxService notificationOutboxService;
    private HolidaySyncService holidaySyncService;
    private CommonService commonService;
    private TeamService teamService;
    private CurrentAuthorityService currentAuthorityService;
    private EmployeeCacheInvalidator employeeCacheInvalidator;
    private LeaveRequestService leaveRequestService;

    @BeforeEach
    void setUp() {
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        employeeLeaveService = mock(EmployeeLeaveService.class);
        notificationOutboxService = mock(NotificationOutboxService.class);
        holidaySyncService = mock(HolidaySyncService.class);
        commonService = mock(CommonService.class);
        teamService = mock(TeamService.class);
        currentAuthorityService = mock(CurrentAuthorityService.class);
        employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);

        leaveRequestService = new LeaveRequestService(
                leaveRequestRepository,
                employeeRepository,
                employeeLeaveService,
                notificationOutboxService,
                holidaySyncService,
                commonService,
                teamService,
                currentAuthorityService,
                employeeCacheInvalidator,
                clock
        );
    }

    @Test
    void getLeaveRequestDetail_missingRequest_returnsNotFound() {
        when(leaveRequestRepository.findDetailById(999L))
                .thenReturn(java.util.Optional.empty());

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> leaveRequestService.getLeaveRequestDetail(999L, EMPLOYEE_ID)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void searchLeaveRequests_withoutCallerContext_redactsPrivateFields() {
        LeaveRequest leaveRequest = mock(
                LeaveRequest.class,
                org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(leaveRequest.getStatus()).thenReturn(LeaveRequestStatus.REJECTED);
        when(leaveRequest.getRejectReason()).thenReturn("비공개 반려 사유");

        LeaveRequestListDto.LeaveRequestListRequest condition =
                new LeaveRequestListDto.LeaveRequestListRequest(
                        2L,
                        "타인",
                        REQUEST_DATE,
                        REQUEST_DATE,
                        LeaveRequestStatus.REJECTED,
                        "타인");

        when(leaveRequestRepository.searchLeaveRequests(
                eq(2L),
                eq(REQUEST_DATE),
                eq(REQUEST_DATE),
                eq(LeaveRequestStatus.REJECTED),
                org.mockito.ArgumentMatchers.isNull(),
                eq("타인")
        )).thenReturn(List.of(leaveRequest));

        var result = leaveRequestService.searchLeaveRequests(condition);

        assertEquals(1, result.size());
        assertNull(result.get(0).getRejectReason());
        verifyNoInteractions(currentAuthorityService);
    }

    @Test
    void remainingDays_usesFiscalYearPeriod() {
        Employee employee = mockEmployee();
        CommonService service = new CommonService(leaveRequestRepository, employeeLeaveService, clock);

        when(leaveRequestRepository.sumRequestedUseDays(
                eq(EMPLOYEE_ID),
                eq(List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 12, 31))
        )).thenReturn(1.0f);
        when(employeeLeaveService.getAdjustedLeaveDays(EMPLOYEE_ID, "2026")).thenReturn(0.5f);

        assertEquals(14.5f, service.getRemainingDays(employee), 0.001f);

        verify(leaveRequestRepository).sumRequestedUseDays(
                EMPLOYEE_ID,
                List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );
        verify(employeeLeaveService).getAdjustedLeaveDays(EMPLOYEE_ID, "2026");
    }

    @Test
    void createLeaveRequest_snapshotDiffIsOnlyCurrentRequest() {
        Employee employee = mockEmployee();
        prepareCreateRequest(employee, LeaveType.FULL, 1.0f);

        LeaveRequest saved = captureSavedRequest();

        assertEquals(8.0f, saved.getPrevTotalLeaveDays(), 0.001f);
        assertEquals(7.0f, saved.getCurrTotalLeaveDays(), 0.001f);
    }

    @Test
    void createSpecialLeaveRequest_doesNotDeductAnnualLeaveSnapshot() {
        Employee employee = mockEmployee();
        prepareCreateRequest(employee, LeaveType.ALTERNATIVE, 1.0f);

        LeaveRequest saved = captureSavedRequest();

        assertEquals(8.0f, saved.getPrevTotalLeaveDays(), 0.001f);
        assertEquals(8.0f, saved.getCurrTotalLeaveDays(), 0.001f);
    }

    @Test
    void createSpecialLeaveRequest_rejectsUseDaysThatDoNotMatchBusinessDays() {
        Employee employee = mockEmployee();
        when(employeeRepository.findByIdForUpdate(EMPLOYEE_ID)).thenReturn(java.util.Optional.of(employee));
        when(employeeLeaveService.getCalculatedCurrYearLeaveDays(employee)).thenReturn(15.0f);
        when(holidaySyncService.findByYearRange(2026, 2026)).thenReturn(List.of());

        org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> leaveRequestService.createLeaveRequest(
                        EMPLOYEE_ID,
                        createRequest(LeaveType.FAMILY.getName(), REQUEST_DATE, 2.0f)
                )
        );
        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    private Employee mockEmployee() {
        Employee employee = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(EMPLOYEE_ID);
        when(employee.getHireDate()).thenReturn(HIRE_DATE);
        when(employee.getCurrYear()).thenReturn("2026");
        when(employee.getCurrTotalLeaveDays()).thenReturn(15.0f);
        when(employee.isActive(any(LocalDate.class))).thenReturn(true);
        return employee;
    }

    private void prepareCreateRequest(Employee employee, LeaveType leaveType, float useDays) {
        LeaveRequest approved = mockActiveRequest(LeaveRequestStatus.APPROVED, 5.0f);
        LeaveRequest pending = mockActiveRequest(LeaveRequestStatus.PENDING, 2.0f);

        when(employeeRepository.findByIdForUpdate(EMPLOYEE_ID)).thenReturn(java.util.Optional.of(employee));
        when(employeeLeaveService.getCalculatedCurrYearLeaveDays(employee)).thenReturn(15.0f);
        when(commonService.getRemainingDays(employee)).thenReturn(8.0f);
        when(commonService.getRemainingDays(employee, 15.0f, 7.0f)).thenReturn(8.0f);
        when(holidaySyncService.findByYearRange(2026, 2026)).thenReturn(List.of());
        when(leaveRequestRepository.searchLeaveRequests(
                eq(EMPLOYEE_ID),
                eq(REQUEST_DATE),
                eq(REQUEST_DATE),
                any(),
                any(),
                any()
        )).thenReturn(List.of());
        when(leaveRequestRepository.findActiveLeaveRequests(
                EMPLOYEE_ID,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(approved, pending));
        when(teamService.refreshApproverIds(employee)).thenReturn(Set.of());

        leaveRequestService.createLeaveRequest(
                EMPLOYEE_ID,
                createRequest(leaveType.getName(), REQUEST_DATE, useDays)
        );
    }

    private LeaveRequest mockActiveRequest(LeaveRequestStatus status, float useDays) {
        LeaveRequest request = mock(LeaveRequest.class);
        when(request.getLeaveType()).thenReturn(LeaveType.FULL.getName());
        when(request.getStatus()).thenReturn(status);
        when(request.getUseDays()).thenReturn(useDays);
        return request;
    }

    private LeaveRequest captureSavedRequest() {
        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        return captor.getValue();
    }

    private static LeaveRequestDto.LeaveRequestCreateRequest createRequest(
            String leaveType,
            LocalDate date,
            float useDays
    ) {
        LeaveRequestDto.LeaveRequestCreateRequest request = new LeaveRequestDto.LeaveRequestCreateRequest();
        setField(request, "leaveType", leaveType);
        setField(request, "startDate", date);
        setField(request, "endDate", date);
        setField(request, "useDays", useDays);
        return request;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
