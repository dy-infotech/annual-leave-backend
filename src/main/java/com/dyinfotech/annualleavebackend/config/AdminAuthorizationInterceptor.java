package com.dyinfotech.annualleavebackend.config;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.common.security.RequirePersonnelAuthority;
import com.dyinfotech.annualleavebackend.common.security.ReplayAwareApproval;
import com.dyinfotech.annualleavebackend.service.CurrentAuthorityService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * /api/admin/**의 권한 검증을 중앙화한다.
 * 기본값은 current admin이며, @RequirePersonnelAuthority가 있으면 인사권 검증으로 대체한다.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthorizationInterceptor implements HandlerInterceptor {

    private final CurrentAuthorityService currentAuthorityService;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof EmployeePrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        if (handler instanceof HandlerMethod handlerMethod) {
            if (isReplayAwareApproval(handlerMethod)) {
                // 승인/반려는 서비스가 조직 mutex 아래에서 동일 결과 재전송을 먼저 판정하고,
                // 새 상태 변경일 때만 최신 DB 결재권을 검증한다.
                return true;
            }
            if (requiresPersonnelAuthority(handlerMethod)) {
                currentAuthorityService.requireAuthenticatedPersonnelAuthority(
                        principal.personnelAuthority());
                return true;
            }
        }

        currentAuthorityService.requireAuthenticatedAdmin(principal.employeeId());
        return true;
    }

    private boolean isReplayAwareApproval(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(
                handlerMethod.getMethod(),
                ReplayAwareApproval.class);
    }

    private boolean requiresPersonnelAuthority(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(
                        handlerMethod.getMethod(),
                        RequirePersonnelAuthority.class)
                || AnnotatedElementUtils.hasAnnotation(
                        handlerMethod.getBeanType(),
                        RequirePersonnelAuthority.class);
    }
}
