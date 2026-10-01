package com.dyinfotech.annualleavebackend.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.common.security.RequirePersonnelAuthority;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.service.CurrentAuthorityService;

class AdminAuthorizationInterceptorRegressionTest {

    private static final Long EMPLOYEE_ID = 10L;

    private CurrentAuthorityService currentAuthorityService;
    private AdminAuthorizationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        currentAuthorityService = mock(CurrentAuthorityService.class);
        interceptor = new AdminAuthorizationInterceptor(currentAuthorityService);

        EmployeePrincipal principal =
                new EmployeePrincipal(EMPLOYEE_ID, Role.EMPLOYEE, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void defaultAdminRoute_usesCurrentAdminRegardlessOfJwtRole() throws Exception {
        HandlerMethod handler = handler(new DefaultAdminController(), "endpoint");

        interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/admin/test"),
                new MockHttpServletResponse(),
                handler);

        verify(currentAuthorityService).requireAuthenticatedAdmin(EMPLOYEE_ID);
        verify(currentAuthorityService, never()).requirePersonnelAuthority(EMPLOYEE_ID);
    }

    @Test
    void personnelAnnotation_replacesDefaultAdminCheck() throws Exception {
        HandlerMethod handler = handler(new PersonnelController(), "endpoint");

        interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/admin/personnel"),
                new MockHttpServletResponse(),
                handler);

        verify(currentAuthorityService).requirePersonnelAuthority(EMPLOYEE_ID);
        verify(currentAuthorityService, never()).requireAuthenticatedAdmin(EMPLOYEE_ID);
    }

    private HandlerMethod handler(Object controller, String methodName) throws Exception {
        Method method = controller.getClass().getDeclaredMethod(methodName);
        return new HandlerMethod(controller, method);
    }

    static class DefaultAdminController {
        public void endpoint() {
        }
    }

    @RequirePersonnelAuthority
    static class PersonnelController {
        public void endpoint() {
        }
    }
}
