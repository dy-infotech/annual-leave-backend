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
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.type.LeaveType;
import com.dyinfotech.annualleavebackend.common.util.DateUtils;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.domain.support.CreatedAudit;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

class LeaveRequestIdempotencyRegressionTest {

    private static final long EMPLOYEE_ID = 7L;
    private static final String REQUEST_KEY = "leave-command-20261001-0001";
    private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 10, 5);

    private LeaveRequestRepository leaveRequestRepository;
    private EmployeeRepository employeeRepository;
    private LeaveRequestService service;

    @BeforeEach
    void setUp() {
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        employeeRepository = mock(EmployeeRepository.class);

        service = new LeaveRequestService(
                leaveRequestRepository,
                employeeRepository,
                mock(EmployeeLeaveService.class),
                mock(NotificationOutboxService.class),
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
    void sameKeyAndPayload_replaysOriginalResultWithoutCreatingAnotherRow()
            throws Exception {
        LeaveRequestDto.LeaveRequestCreateRequest request =
                request("동일 요청");
        Employee employee = mock(Employee.class);
        when(employeeRepository.findByIdForUpdate(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee));

        LeaveRequest existing = existingRequest(
                hash(request),
                123L,
                "동일 요청");
        when(leaveRequestRepository
                .findByEmployee_EmployeeIdAndCreateRequestKey(
                        EMPLOYEE_ID,
                        REQUEST_KEY))
                .thenReturn(Optional.of(existing));

        var response = service.createLeaveRequest(
                EMPLOYEE_ID,
                request,
                REQUEST_KEY);

        assertEquals(123L, response.getRequestId());
        verify(leaveRequestRepository, never())
                .saveAndFlush(any(LeaveRequest.class));
    }

    @Test
    void sameKeyWithDifferentPayload_isRejectedAsConflict()
            throws Exception {
        LeaveRequestDto.LeaveRequestCreateRequest request =
                request("새 payload");
        Employee employee = mock(Employee.class);
        when(employeeRepository.findByIdForUpdate(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee));

        LeaveRequest existing = existingRequest(
                hash(request("기존 payload")),
                123L,
                "기존 payload");
        when(leaveRequestRepository
                .findByEmployee_EmployeeIdAndCreateRequestKey(
                        EMPLOYEE_ID,
                        REQUEST_KEY))
                .thenReturn(Optional.of(existing));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createLeaveRequest(
                        EMPLOYEE_ID,
                        request,
                        REQUEST_KEY));

        assertEquals(409, error.getStatusCode().value());
        verify(leaveRequestRepository, never())
                .saveAndFlush(any(LeaveRequest.class));
    }

    private LeaveRequest existingRequest(
            String requestHash,
            Long requestId,
            String reason) {
        LeaveRequest existing = mock(LeaveRequest.class);
        CreatedAudit audit = mock(CreatedAudit.class);

        when(existing.getCreateRequestHash()).thenReturn(requestHash);
        when(existing.getRequestId()).thenReturn(requestId);
        when(existing.getLeaveType()).thenReturn(LeaveType.FULL.getName());
        when(existing.getStartDate()).thenReturn(REQUEST_DATE);
        when(existing.getEndDate()).thenReturn(REQUEST_DATE);
        when(existing.getUseDays()).thenReturn(1.0f);
        when(existing.getStatus()).thenReturn(LeaveRequestStatus.PENDING);
        when(existing.getLeaveReason()).thenReturn(reason);
        when(existing.getCreatedAudit()).thenReturn(audit);
        return existing;
    }

    private LeaveRequestDto.LeaveRequestCreateRequest request(String reason) {
        LeaveRequestDto.LeaveRequestCreateRequest request =
                new LeaveRequestDto.LeaveRequestCreateRequest();
        setField(request, "leaveType", LeaveType.FULL.getName());
        setField(request, "startDate", REQUEST_DATE);
        setField(request, "endDate", REQUEST_DATE);
        setField(request, "useDays", 1.0f);
        setField(request, "leaveReason", reason);
        return request;
    }

    private String hash(LeaveRequestDto.LeaveRequestCreateRequest request)
            throws Exception {
        String reason = request.getLeaveReason() == null
                ? ""
                : request.getLeaveReason().trim();
        String canonical = EMPLOYEE_ID
                + "|" + LeaveType.FULL.name()
                + "|" + request.getStartDate()
                + "|" + request.getEndDate()
                + "|" + DateUtils.toMinutes(request.getUseDays())
                + "|" + reason.length() + ":" + reason;
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
