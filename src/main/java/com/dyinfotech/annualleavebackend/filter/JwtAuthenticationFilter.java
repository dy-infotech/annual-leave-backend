package com.dyinfotech.annualleavebackend.filter;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.common.security.jwt.JwtProvider;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;
    private final JwtProvider jwtProvider;
    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        // 토큰이 있으면 현재 계정 상태까지 확인해 인증 정보를 구성한다
        if (token != null) {
            final Long employeeId;
            final String roleData;
            final Role role;
            final String tokenCredentialVersion;
            try {
                Claims claims = jwtProvider.parseVerifiedClaims(token);
                employeeId = Long.valueOf(claims.getSubject());
                roleData = claims.get("role", String.class);
                role = Role.getRole(roleData);
                tokenCredentialVersion = claims.get("credentialVersion", String.class);
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("[인증 실패] 유효하지 않거나 만료된 JWT: {}", e.getMessage());
                sendUnauthorizedResponse(response, "유효하지 않거나 만료된 인증 정보입니다. 다시 로그인해주세요.");
                return;
            }

            if (role == null || tokenCredentialVersion == null) {
                log.warn("[인증 실패] JWT claim이 유효하지 않습니다. employeeId: {}, role: {}", employeeId, roleData);
                sendUnauthorizedResponse(response, "유효하지 않은 토큰 권한 정보입니다.");
                return;
            }

            // 현재 직원 상태와 비밀번호 변경 여부를 다시 확인한다
            var employee = employeeRepository.findById(employeeId).orElse(null);
            if (employee == null || !employee.isActive(LocalDate.now(clock))) {
                sendUnauthorizedResponse(response, "현재 사용할 수 없는 계정입니다. 다시 로그인해주세요.");
                return;
            }

            String currentCredentialVersion = jwtProvider.createCredentialVersion(employee.getPassword());
            if (tokenCredentialVersion == null
                    || !Objects.equals(tokenCredentialVersion, currentCredentialVersion)) {
                sendUnauthorizedResponse(response, "인증 정보가 변경되었습니다. 다시 로그인해주세요.");
                return;
            }

            // 토큰의 역할은 인증 정보로만 보관하고 실제 권한은 별도로 확인한다
            var authentication = new UsernamePasswordAuthenticationToken(
                    new EmployeePrincipal(employeeId, role, employee.hasPersonnelAuthority()),
                    null,
                    List.of()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 인증 처리가 끝나면 다음 필터로 요청을 넘긴다
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        String prefix = "Bearer ";
        if (bearerToken != null && bearerToken.startsWith(prefix)) {
            return bearerToken.substring(prefix.length());
        }
        return null;
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        String jsonResponse = objectMapper.writeValueAsString(Map.of(
                "status", HttpServletResponse.SC_UNAUTHORIZED,
                "error", "Unauthorized",
                "message", message));
        response.getWriter().write(jsonResponse);
    }
}
