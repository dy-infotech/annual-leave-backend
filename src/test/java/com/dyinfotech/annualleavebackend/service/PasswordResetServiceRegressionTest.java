package com.dyinfotech.annualleavebackend.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.PasswordResetToken;
import com.dyinfotech.annualleavebackend.dto.FindDataDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.PasswordResetTokenRepository;

class PasswordResetServiceRegressionTest {

    @Test
    void confirmReset_locksEmployeeBeforeResetToken() {
        EmployeeService employeeService = mock(EmployeeService.class);
        PasswordResetTokenRepository tokenRepository = mock(PasswordResetTokenRepository.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthRateLimitService authRateLimitService = mock(AuthRateLimitService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

        PasswordResetService service = new PasswordResetService(
                employeeService,
                tokenRepository,
                employeeRepository,
                transactionTemplate,
                passwordEncoder,
                authRateLimitService,
                mailSender,
                clock);

        FindDataDto.ResetPasswordRequest request = mock(FindDataDto.ResetPasswordRequest.class);
        when(request.getToken()).thenReturn("reset-token");
        when(request.getNewPassword()).thenReturn("new-password");

        PasswordResetToken candidate = mock(PasswordResetToken.class);
        PasswordResetToken lockedToken = mock(PasswordResetToken.class);
        Employee employee = mock(Employee.class);

        when(candidate.getEmployeeId()).thenReturn(7L);
        when(lockedToken.getEmployeeId()).thenReturn(7L);
        when(lockedToken.isExpired(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)))
                .thenReturn(false);
        when(employee.getEmployeeId()).thenReturn(7L);
        when(employee.getPassword()).thenReturn("old-hash");

        when(tokenRepository.findByTokenHashAndConsumedAtIsNull(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(candidate));
        when(employeeRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(employee));
        when(tokenRepository.findUnusedForUpdate(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(lockedToken));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(employeeService.compareAndSetPassword(7L, "old-hash", "new-hash")).thenReturn(true);

        service.confirmReset(request);

        var order = inOrder(tokenRepository, employeeRepository);
        order.verify(tokenRepository)
                .findByTokenHashAndConsumedAtIsNull(org.mockito.ArgumentMatchers.anyString());
        order.verify(employeeRepository).findByIdForUpdate(7L);
        order.verify(tokenRepository)
                .findUnusedForUpdate(org.mockito.ArgumentMatchers.anyString());
    }
}
