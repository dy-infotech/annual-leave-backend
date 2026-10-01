package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.type.LeaveType;
import com.dyinfotech.annualleavebackend.common.util.DateUtils;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

class LeaveCreateIdempotencyRegressionTest {

    private static final Long EMPLOYEE_ID = 7L;
    private static final String REQUEST_KEY = "leave-request-00000001";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 2);

    private LeaveRequestRepository leaveRequestRepository;
    private EmployeeRepository employeeRepository;
    private NotificationOutboxService notificationOutboxService;
    private LeaveRequestService service;

    @BeforeEach
    void setUp() {
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        notificationOutboxService = mock(NotificationOutboxService.class);

        service = new LeaveRequestService(
                leaveRequestRepository,
                employeeRepository,
                mock(EmployeeLeaveService.class),
                notificationOutboxService,
                mock(HolidaySyncService.class),
                mock(CommonService.class),
                mock(TeamService.class),
                mock(CurrentAuthorityService.class),
                mock(EmployeeCacheInvalidator.class),
                Clock.fixed(
                        Instant.parse("2026-10-01T00:00:00Z"),
                        ZoneId.of("Asia/Seoul")));
    }

    @Test
    void sameKeyAndPayload_replaysExistingSuccessWithoutSecondInsert() throws Exception {
        LeaveRequestDto.LeaveRequestCreateRequest request = createRequest();
        Employee employee = mock(Employee.class);
        LeaveRequest existing = mock(LeaveRequest.class, Answers.RETURNS_DEEP_STUBS);

        when(employeeRepository.findByIdForUpdate(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee));
        when(leaveRequestRepository.findByEmployee_EmployeeIdAndCreateRequestKey(
                EMPLOYEE_ID, REQUEST_KEY))
                .thenReturn(Optional.of(existing));

        when(existing.getCreateRequestHash()).thenReturn(hash(request));
        when(existing.getRequestId()).thenReturn(101L);
        when(existing.getLeaveType()).thenReturn(LeaveType.FULL.getName());
        when(existing.getStartDate()).thenReturn(DATE);
        when(existing.getEndDate()).thenReturn(DATE);
        when(existing.getUseDays()).thenReturn(1.0f);
        when(existing.getStatus()).thenReturn(LeaveRequestStatus.PENDING);

        var response = service.createLeaveRequest(EMPLOYEE_ID, request, REQUEST_KEY);

        assertEquals(101L, response.getRequestId());
        verify(leaveRequestRepository, never()).saveAndFlush(any(LeaveRequest.class));
        verify(notificationOutboxService, never())
                .enqueueTeams(any(), any(), any());
    }

    @Test
    void sameKeyWithDifferentPayload_returnsConflict() {
        LeaveRequestDto.LeaveRequestCreateRequest request = createRequest();
        Employee employee = mock(Employee.class);
        LeaveRequest existing = mock(LeaveRequest.class);

        when(employeeRepository.findByIdForUpdate(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee));
        when(leaveRequestRepository.findByEmployee_EmployeeIdAndCreateRequestKey(
                EMPLOYEE_ID, REQUEST_KEY))
                .thenReturn(Optional.of(existing));
        when(existing.getCreateRequestHash()).thenReturn("different-payload-hash");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createLeaveRequest(EMPLOYEE_ID, request, REQUEST_KEY));

        assertEquals(409, error.getStatusCode().value());
        verify(leaveRequestRepository, never()).saveAndFlush(any(LeaveRequest.class));
    }

    private LeaveRequestDto.LeaveRequestCreateRequest createRequest() {
        LeaveRequestDto.LeaveRequestCreateRequest request =
                new LeaveRequestDto.LeaveRequestCreateRequest();
        setField(request, "leaveType", LeaveType.FULL.getName());
        setField(request, "startDate", DATE);
        setField(request, "endDate", DATE);
        setField(request, "useDays", 1.0f);
        setField(request, "leaveReason", null);
        return request;
    }

    private String hash(LeaveRequestDto.LeaveRequestCreateRequest request)
            throws Exception {
        String canonical = EMPLOYEE_ID
                + "|" + LeaveType.FULL.name()
                + "|" + request.getStartDate()
                + "|" + request.getEndDate()
                + "|" + DateUtils.toMinutes(request.getUseDays())
                + "|0:";
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
