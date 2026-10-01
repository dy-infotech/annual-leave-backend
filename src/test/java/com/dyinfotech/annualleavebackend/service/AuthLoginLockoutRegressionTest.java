package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.common.security.jwt.JwtProvider;
import com.dyinfotech.annualleavebackend.common.type.BasisDataType;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;

class AuthLoginLockoutRegressionTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-01T02:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void correctPassword_recoversFromFailureThresholdInsteadOfRemainingLocked() {
        BasisDataFactory basisDataFactory = mock(BasisDataFactory.class);
        EmployeeService employeeService = mock(EmployeeService.class);
        EmployeeLeaveService employeeLeaveService = mock(EmployeeLeaveService.class);
        TeamService teamService = mock(TeamService.class);
        Employee employee = mock(Employee.class);
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        when(basisDataFactory.getAsInteger(BasisDataType.LOGIN_FAIL_MAX_COUNT))
                .thenReturn(Optional.of(30));
        when(basisDataFactory.getAsInteger(BasisDataType.LOGIN_UNBLOCK_HOUR))
                .thenReturn(Optional.of(24));
        when(employee.getEmployeeId()).thenReturn(10L);
        when(employee.getAccessCount()).thenReturn(30);
        when(employee.getAccessedAt()).thenReturn(LocalDateTime.of(2026, 10, 1, 10, 55));
        when(employee.getPassword()).thenReturn(passwordEncoder.encode("correct-password"));
        when(employee.getCurrTotalLeaveDays()).thenReturn(0.0f);
        when(employeeLeaveService.getCalculatedCurrYearLeaveDays(employee)).thenReturn(0.0f);
        when(teamService.resolveCurrentApproverIds(employee)).thenReturn(Set.of());

        AuthService service = new AuthService(
                basisDataFactory,
                mock(EmployeeRepository.class),
                passwordEncoder,
                mock(JwtProvider.class),
                employeeLeaveService,
                mock(NotificationService.class),
                mock(DepartmentService.class),
                employeeService,
                teamService,
                mock(AuthRateLimitService.class),
                CLOCK,
                mock(JavaMailSender.class));

        assertDoesNotThrow(() -> service.validateLogin(employee, "correct-password"));

        verify(employeeService).resetAccessCount(10L, LocalDateTime.now(CLOCK));
        verify(employeeService, never()).increaseAccessCount(any(), any());
    }

    @Test
    void wrongPassword_atFailureThresholdRemainsBlocked() {
        BasisDataFactory basisDataFactory = mock(BasisDataFactory.class);
        EmployeeService employeeService = mock(EmployeeService.class);
        Employee employee = mock(Employee.class);
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        when(basisDataFactory.getAsInteger(BasisDataType.LOGIN_FAIL_MAX_COUNT))
                .thenReturn(Optional.of(30));
        when(basisDataFactory.getAsInteger(BasisDataType.LOGIN_UNBLOCK_HOUR))
                .thenReturn(Optional.of(24));
        when(employee.getEmployeeId()).thenReturn(10L);
        when(employee.getAccessCount()).thenReturn(30);
        when(employee.getAccessedAt()).thenReturn(LocalDateTime.of(2026, 10, 1, 10, 55));
        when(employee.getPassword()).thenReturn(passwordEncoder.encode("correct-password"));

        AuthService service = new AuthService(
                basisDataFactory,
                mock(EmployeeRepository.class),
                passwordEncoder,
                mock(JwtProvider.class),
                mock(EmployeeLeaveService.class),
                mock(NotificationService.class),
                mock(DepartmentService.class),
                employeeService,
                mock(TeamService.class),
                mock(AuthRateLimitService.class),
                CLOCK,
                mock(JavaMailSender.class));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateLogin(employee, "wrong-password"));

        assertEquals(401, exception.getStatusCode().value());
        verify(employeeService, never()).resetAccessCount(any(), any());
        verify(employeeService, never()).increaseAccessCount(any(), any());
    }
}
