package com.dyinfotech.annualleavebackend.filter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dyinfotech.annualleavebackend.common.security.LoginPrincipal;
import com.dyinfotech.annualleavebackend.common.security.jwt.JwtProvider;
import com.dyinfotech.annualleavebackend.common.type.Role;

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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && jwtProvider.validateToken(token)) {
            Long employeeId = jwtProvider.getEmployeeId(token);
            String roleData = jwtProvider.getRole(token);
            
            // Role 검증 (XXX: 테스트 필요)
            Role role = Role.getRole(roleData);
            if (role == null) {
            	log.warn("[인증 실패] 유효하지 않은 Role입니다. employeeId: {}, role: {}", employeeId, roleData);
                
                // 다음 필터로 넘기지 않고(filterChain.doFilter 생략) 즉시 클라이언트에게 에러를 응답합니다.
                sendUnauthorizedResponse(response, "유효하지 않은 토큰 권한 정보입니다.");
                return;
            }
            
            var authorities = List.of(new SimpleGrantedAuthority(role.authority()));

            var authentication = new UsernamePasswordAuthenticationToken(
                    // 컨트롤러에서 @AuthenticationPrincipal로 꺼내 쓸 수 있음
                    new LoginPrincipal(employeeId, role),   // 1. principal (인증된 주체)
                    null,         // 2. credentials (비밀번호 - 이미 인증이 끝났으니 불필요)
                    authorities   // 3. authorities (부여된 권한)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    // "Authorization: Bearer {token}" 헤더에서 토큰만 추출
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        
        String prefix = "Bearer ";
        if (bearerToken != null && bearerToken.startsWith(prefix)) {
            return bearerToken.substring(prefix.length());
        }

        return null;
    }
    
    /**
     * 인증 실패 시 클라이언트에게 401 Unauthorized JSON 응답을 명확하게 내려주는 헬퍼 메서드
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        
        // ObjectMapper를 사용해 객체를 JSON 문자열로 변환(직렬화)
        String jsonResponse = objectMapper.writeValueAsString(Map.of("status", HttpServletResponse.SC_UNAUTHORIZED,
        															"error", "Unauthorized",
        															"message", message));
        
        response.getWriter().write(jsonResponse);
    }
}