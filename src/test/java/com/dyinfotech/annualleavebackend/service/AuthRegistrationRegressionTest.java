package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.common.security.jwt.JwtProvider;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.dto.RegisterDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;

class AuthRegistrationRegressionTest {

    @Test
    void registerEmployee_duplicateEmployeeNumber_returnsConflictBeforeOrganizationWrite() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        Employee approver = mock(Employee.class);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(approver));
        when(employeeRepository.existsByEmployeeNumber("A2026999")).thenReturn(true);

        AuthService authService = new AuthService(
                mock(BasisDataFactory.class),
                employeeRepository,
                mock(PasswordEncoder.class),
                mock(JwtProvider.class),
                mock(EmployeeLeaveService.class),
                mock(NotificationService.class),
                departmentService,
                mock(EmployeeService.class),
                mock(TeamService.class),
                mock(AuthRateLimitService.class),
                Clock.fixed(
                        Instant.parse("2026-09-30T00:00:00Z"),
                        ZoneId.of("Asia/Seoul")),
                mock(JavaMailSender.class)
        );

        RegisterDto.RegisterRequest request = new RegisterDto.RegisterRequest();
        setField(request, "employeeNumber", "A2026999");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.registerEmployee(1L, request)
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals("이미 등록된 사번입니다.", exception.getReason());
        verifyNoInteractions(departmentService);
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
