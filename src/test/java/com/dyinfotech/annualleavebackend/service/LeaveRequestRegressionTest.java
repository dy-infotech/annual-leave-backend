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
    private NotificationService notificationService;
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
        notificationService = mock(NotificationService.class);
        holidaySyncService = mock(HolidaySyncService.class);
        commonService = mock(CommonService.class);
        teamService = mock(TeamService.class);
        currentAuthorityService = mock(CurrentAuthorityService.class);
        employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);

        leaveRequestService = new LeaveRequestService(
                leaveRequestRepository,
                employeeRepository,
                employeeLeaveService,
                notificationService,
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
    void searchManagedLeaveRequests_withoutManagedTeam_isForbidden() {
        when(teamService.findManagedTeams(EMPLOYEE_ID)).thenReturn(List.of());

        LeaveRequestListDto.LeaveRequestListRequest condition =
                new LeaveRequestListDto.LeaveRequestListRequest(
                        null, null, REQUEST_DATE, REQUEST_DATE, null, null);

        ResponseStatusException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        ResponseStatusException.class,
                        () -> leaveRequestService.searchManagedLeaveRequestsPage(
                                condition,
                                EMPLOYEE_ID,
                                0,
                                50,
                                null,
                                null));

        assertEquals(403, exception.getStatusCode().value());
        verify(leaveRequestRepository, never()).searchLeaveRequestsPage(
                any(), any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void searchManagedLeaveRequests_limitsQueryToManagedHierarchy() {
        TeamService.ManagedTeam root = managedTeam(10L, "관리팀", 10L, "관리팀");
        TeamService.ManagedTeam child = managedTeam(11L, "하위팀", 10L, "관리팀");
        when(teamService.findManagedTeams(EMPLOYEE_ID)).thenReturn(List.of(root));
        when(teamService.getSelfAndDescendants("관리팀"))
                .thenReturn(Set.of(root, child));
        when(leaveRequestRepository.searchLeaveRequestsPage(
                org.mockito.ArgumentMatchers.isNull(),
                eq(REQUEST_DATE),
                eq(REQUEST_DATE),
                org.mockito.ArgumentMatchers.isNull(),
                eq(Set.of("관리팀", "하위팀")),
                org.mockito.ArgumentMatchers.isNull(),
                eq(0),
                eq(50)))
                .thenReturn(List.of());

        LeaveRequestListDto.LeaveRequestListRequest condition =
                new LeaveRequestListDto.LeaveRequestListRequest(
                        null, null, REQUEST_DATE, REQUEST_DATE, null, null);

        var result = leaveRequestService.searchManagedLeaveRequestsPage(
                condition,
                EMPLOYEE_ID,
                0,
                50,
                null,
                null);

        assertEquals(0, result.size());
        verify(leaveRequestRepository).searchLeaveRequestsPage(
                org.mockito.ArgumentMatchers.isNull(),
                eq(REQUEST_DATE),
                eq(REQUEST_DATE),
                org.mockito.ArgumentMatchers.isNull(),
                eq(Set.of("관리팀", "하위팀")),
                org.mockito.ArgumentMatchers.isNull(),
                eq(0),
                eq(50));
    }

    @Test
    void getLeaveRequestDetail_adminInsideManagedHierarchy_keepsPrivateDetail() {
        Employee requester = mock(Employee.class);
        when(requester.getEmployeeId()).thenReturn(2L);
        when(requester.getTeamName()).thenReturn("하위팀");

        LeaveRequest leaveRequest = mock(
                LeaveRequest.class,
                org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(leaveRequest.getEmployee()).thenReturn(requester);
        when(leaveRequest.getStatus()).thenReturn(LeaveRequestStatus.REJECTED);
        when(leaveRequest.getLeaveReason()).thenReturn("휴가 사유");
        when(leaveRequest.getRejectReason()).thenReturn("반려 사유");
        when(leaveRequestRepository.findDetailById(100L))
                .thenReturn(java.util.Optional.of(leaveRequest));

        TeamService.ManagedTeam root = managedTeam(10L, "관리팀", 10L, "관리팀");
        TeamService.ManagedTeam child = managedTeam(11L, "하위팀", 10L, "관리팀");
        when(currentAuthorityService.isAdmin(EMPLOYEE_ID)).thenReturn(true);
        when(teamService.findManagedTeams(EMPLOYEE_ID)).thenReturn(List.of(root));
        when(teamService.getSelfAndDescendants("관리팀"))
                .thenReturn(Set.of(root, child));

        var response = leaveRequestService.getLeaveRequestDetail(100L, EMPLOYEE_ID);

        assertEquals("휴가 사유", response.getLeaveReason());
        assertEquals("반려 사유", response.getRejectReason());
    }

    @Test
    void getLeaveRequestDetail_ceoOutsideManagedHierarchy_keepsPrivateDetail() {
        Employee requester = mock(Employee.class);
        when(requester.getEmployeeId()).thenReturn(2L);
        when(requester.getTeamName()).thenReturn("타부서팀");

        LeaveRequest leaveRequest = mock(
                LeaveRequest.class,
                org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(leaveRequest.getEmployee()).thenReturn(requester);
        when(leaveRequest.getStatus()).thenReturn(LeaveRequestStatus.REJECTED);
        when(leaveRequest.getLeaveReason()).thenReturn("대표 열람 사유");
        when(leaveRequestRepository.findDetailById(102L))
                .thenReturn(java.util.Optional.of(leaveRequest));
        when(currentAuthorityService.canViewAllLeaveDetails(EMPLOYEE_ID)).thenReturn(true);

        var response = leaveRequestService.getLeaveRequestDetail(102L, EMPLOYEE_ID);

        assertEquals("대표 열람 사유", response.getLeaveReason());
        verify(currentAuthorityService, never()).isAdmin(EMPLOYEE_ID);
        verifyNoInteractions(teamService);
    }

    @Test
    void getLeaveRequestDetail_nonAdminNonOwner_redactsPrivateDetail() {
        Employee requester = mock(Employee.class);
        when(requester.getEmployeeId()).thenReturn(2L);
        when(requester.getTeamName()).thenReturn("타부서팀");

        LeaveRequest leaveRequest = mock(
                LeaveRequest.class,
                org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(leaveRequest.getEmployee()).thenReturn(requester);
        when(leaveRequest.getStatus()).thenReturn(LeaveRequestStatus.REJECTED);
        when(leaveRequest.getLeaveReason()).thenReturn("비공개 휴가 사유");
        when(leaveRequest.getRejectReason()).thenReturn("비공개 반려 사유");
        when(leaveRequest.getPrevTotalLeaveDays()).thenReturn(10.0f);
        when(leaveRequest.getCurrTotalLeaveDays()).thenReturn(9.0f);
        when(leaveRequestRepository.findDetailById(101L))
                .thenReturn(java.util.Optional.of(leaveRequest));
        when(currentAuthorityService.isAdmin(EMPLOYEE_ID)).thenReturn(false);

        var response = leaveRequestService.getLeaveRequestDetail(
                101L,
                EMPLOYEE_ID);

        assertEquals("타부서팀", response.getTeam());
        assertNull(response.getLeaveReason());
        assertNull(response.getRejectReason());
        assertNull(response.getPrevTotalLeaveDays());
        assertNull(response.getCurrTotalLeaveDays());
        verifyNoInteractions(teamService);
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
    void createReasonRequiredLeaveRequest_rejectsBlankReason() {
        Employee employee = mockEmployee();
        when(employeeRepository.findByIdForUpdate(EMPLOYEE_ID))
                .thenReturn(java.util.Optional.of(employee));
        when(employeeLeaveService.getCalculatedCurrYearLeaveDays(employee))
                .thenReturn(15.0f);

        LeaveRequestDto.LeaveRequestCreateRequest request =
                createRequest(LeaveType.FAMILY.getName(), REQUEST_DATE, 1.0f);
        setField(request, "leaveReason", "   ");

        ResponseStatusException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        ResponseStatusException.class,
                        () -> leaveRequestService.createLeaveRequest(
                                EMPLOYEE_ID,
                                request));

        assertEquals(400, exception.getStatusCode().value());
        verify(leaveRequestRepository, never()).saveAndFlush(any(LeaveRequest.class));
    }

    @Test
    void createSpecialLeaveRequest_rejectsUseDaysThatDoNotMatchBusinessDays() {
        Employee employee = mockEmployee();
        when(employeeRepository.findByIdForUpdate(EMPLOYEE_ID)).thenReturn(java.util.Optional.of(employee));
        when(employeeLeaveService.getCalculatedCurrYearLeaveDays(employee)).thenReturn(15.0f);
        when(holidaySyncService.findByYearRange(2026, 2026)).thenReturn(List.of());

        LeaveRequestDto.LeaveRequestCreateRequest request =
                createRequest(LeaveType.FAMILY.getName(), REQUEST_DATE, 2.0f);
        setField(request, "leaveReason", "가족 돌봄");

        org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> leaveRequestService.createLeaveRequest(
                        EMPLOYEE_ID,
                        request
                )
        );
        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    private TeamService.ManagedTeam managedTeam(
            Long teamId,
            String teamName,
            Long parentTeamId,
            String parentTeamName) {
        return new TeamService.ManagedTeam(
                teamId,
                teamName,
                parentTeamId,
                parentTeamName,
                EMPLOYEE_ID,
                "E0001",
                "관리자",
                "팀장",
                null);
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
        when(employeeRepository.findByIdForUpdate(EMPLOYEE_ID)).thenReturn(java.util.Optional.of(employee));
        when(employeeLeaveService.getCalculatedCurrYearLeaveDays(employee)).thenReturn(15.0f);
        when(leaveRequestRepository.sumRequestedUseDays(
                eq(EMPLOYEE_ID),
                eq(List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 12, 31))
        )).thenReturn(7.0f);
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
        when(teamService.refreshApproverIds(employee)).thenReturn(Set.of());

        LeaveRequestDto.LeaveRequestCreateRequest request =
                createRequest(leaveType.getName(), REQUEST_DATE, useDays);
        if (leaveType.requiresReason()) {
            setField(request, "leaveReason", "정상 신청 사유");
        }

        leaveRequestService.createLeaveRequest(
                EMPLOYEE_ID,
                request
        );

        verify(leaveRequestRepository).sumRequestedUseDays(
                EMPLOYEE_ID,
                List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        verify(leaveRequestRepository, never()).findActiveLeaveRequests(
                any(), any(), any());
    }

    private LeaveRequest captureSavedRequest() {
        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).saveAndFlush(captor.capture());
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
