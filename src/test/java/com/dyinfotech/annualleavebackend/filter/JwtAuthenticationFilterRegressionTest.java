package com.dyinfotech.annualleavebackend.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.dyinfotech.annualleavebackend.common.security.jwt.JwtProvider;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;

import io.jsonwebtoken.Claims;
import tools.jackson.databind.ObjectMapper;

class JwtAuthenticationFilterRegressionTest {

    @Test
    void authenticatedRequest_parsesVerifiedClaimsOnlyOnce() throws Exception {
        JwtProvider jwtProvider = mock(JwtProvider.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        Claims claims = mock(Claims.class);
        Employee employee = mock(Employee.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);

        when(jwtProvider.parseVerifiedClaims("token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("7");
        when(claims.get(eq("role"), eq(String.class))).thenReturn("EMPLOYEE");
        when(claims.get(eq("credentialVersion"), eq(String.class))).thenReturn("credential-v1");
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(employee.isActive(LocalDate.of(2026, 10, 1))).thenReturn(true);
        when(employee.getPassword()).thenReturn("bcrypt-hash");
        when(jwtProvider.createCredentialVersion("bcrypt-hash")).thenReturn("credential-v1");

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new ObjectMapper(),
                jwtProvider,
                employeeRepository,
                clock);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(jwtProvider).parseVerifiedClaims("token");
        verify(jwtProvider, never()).validateToken("token");
        verify(jwtProvider, never()).getEmployeeId("token");
        verify(jwtProvider, never()).getRole("token");
        verify(jwtProvider, never()).getCredentialVersion("token");
    }
}
