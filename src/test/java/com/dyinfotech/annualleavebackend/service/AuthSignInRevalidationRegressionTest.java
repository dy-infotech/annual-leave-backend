package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.common.security.jwt.JwtProvider;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.service.EmployeeLeaveService.EmployeeAuthorityResolver;

class AuthSignInRevalidationRegressionTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-01T00:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void revalidateSignInAccess_locksEmployeeAndIssuesCurrentAccessWhenCredentialUnchanged() {
        String secret = "test-jwt-secret-test-jwt-secret-0123456789";
        JwtProvider jwtProvider = new JwtProvider(secret, 60_000L);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        EmployeeLeaveService employeeLeaveService = mock(EmployeeLeaveService.class);
        EmployeeAuthorityResolver resolver = mock(EmployeeAuthorityResolver.class);
        Employee employee = mock(Employee.class);

        String passwordHash = "$2a$10$012345678901234567890u12345678901234567890123456789012";
        String credentialVersion = jwtProvider.createCredentialVersion(passwordHash);
        String validatedToken =
                jwtProvider.generateToken(10L, Role.EMPLOYEE.name(), credentialVersion);

        when(employeeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(employee));
        when(employee.isActive(LocalDate.now(CLOCK))).thenReturn(true);
        when(employee.getPassword()).thenReturn(passwordHash);
        when(employee.getEmployeeId()).thenReturn(10L);
        when(employee.getName()).thenReturn("테스트");
        when(employee.getEmail()).thenReturn("test@example.com");
        when(employeeLeaveService.createAuthorityResolver(10L)).thenReturn(resolver);
        when(resolver.resolveRole(10L)).thenReturn(Role.EMPLOYEE);

        AuthService service = new AuthService(
                mock(BasisDataFactory.class),
                employeeRepository,
                new BCryptPasswordEncoder(),
                jwtProvider,
                employeeLeaveService,
                mock(NotificationService.class),
                mock(DepartmentService.class),
                mock(EmployeeService.class),
                mock(TeamService.class),
                mock(AuthRateLimitService.class),
                CLOCK,
                mock(JavaMailSender.class));

        var response = service.revalidateSignInAccess(10L, validatedToken);

        assertEquals(10L, response.getEmployeeId());
        assertEquals(
                credentialVersion,
                jwtProvider.getCredentialVersion(response.getToken()));
        verify(employeeRepository).findByIdForUpdate(10L);
    }

    @Test
    void revalidateSignInAccess_rejectsCredentialChangedAfterPasswordValidation() {
        String secret = "test-jwt-secret-test-jwt-secret-0123456789";
        JwtProvider jwtProvider = new JwtProvider(secret, 60_000L);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        Employee employee = mock(Employee.class);

        String validatedHash = "$2a$10$012345678901234567890u12345678901234567890123456789012";
        String currentHash = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDE12345678901234567890123";
        String validatedToken = jwtProvider.generateToken(
                10L,
                Role.EMPLOYEE.name(),
                jwtProvider.createCredentialVersion(validatedHash));

        when(employeeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(employee));
        when(employee.isActive(LocalDate.now(CLOCK))).thenReturn(true);
        when(employee.getPassword()).thenReturn(currentHash);

        AuthService service = new AuthService(
                mock(BasisDataFactory.class),
                employeeRepository,
                new BCryptPasswordEncoder(),
                jwtProvider,
                mock(EmployeeLeaveService.class),
                mock(NotificationService.class),
                mock(DepartmentService.class),
                mock(EmployeeService.class),
                mock(TeamService.class),
                mock(AuthRateLimitService.class),
                CLOCK,
                mock(JavaMailSender.class));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.revalidateSignInAccess(10L, validatedToken));

        assertEquals(401, error.getStatusCode().value());
        verify(employeeRepository).findByIdForUpdate(10L);
    }
}
