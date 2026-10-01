package com.dyinfotech.annualleavebackend.api;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.config.AdminAuthorizationInterceptor;
import com.dyinfotech.annualleavebackend.controller.LeaveApprovalController;
import com.dyinfotech.annualleavebackend.dto.PageResponseDto;
import com.dyinfotech.annualleavebackend.service.CurrentAuthorityService;
import com.dyinfotech.annualleavebackend.service.LeaveApprovalService;

class LeaveApprovalApiContractRegressionTest {

    private static final Long EMPLOYEE_ID = 1L;

    private CurrentAuthorityService currentAuthorityService;
    private LeaveApprovalService leaveApprovalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        currentAuthorityService = mock(CurrentAuthorityService.class);
        leaveApprovalService = mock(LeaveApprovalService.class);

        LeaveApprovalController controller = new LeaveApprovalController(leaveApprovalService);

        EmployeePrincipal principal = new EmployeePrincipal(EMPLOYEE_ID, Role.EMPLOYEE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory) {
                return principal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new AdminAuthorizationInterceptor(currentAuthorityService))
                .setCustomArgumentResolvers(principalResolver)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pending_staleEmployeeJwtStillUsesCurrentAdminCheck() throws Exception {
        when(leaveApprovalService.getPendingRequests(
                EMPLOYEE_ID, 0, 50, null, null))
                .thenReturn(new PageResponseDto<>(List.of(), 0L, false));

        mockMvc.perform(get("/api/admin/leave-requests/pending"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(currentAuthorityService).requireAuthenticatedAdmin(EMPLOYEE_ID);
        verify(leaveApprovalService).getPendingRequests(
                EMPLOYEE_ID, 0, 50, null, null);
    }

    @Test
    void pending_currentAdminDenialIsReturnedAsForbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "인가되지 않은 사용자입니다."))
                .when(currentAuthorityService).requireAuthenticatedAdmin(EMPLOYEE_ID);

        mockMvc.perform(get("/api/admin/leave-requests/pending"))
                .andExpect(status().isForbidden());

        verify(currentAuthorityService).requireAuthenticatedAdmin(EMPLOYEE_ID);
        verifyNoInteractions(leaveApprovalService);
    }

    @Test
    void rejectReasonOverTwoHundredCharactersIsRejectedBeforeService() throws Exception {
        String body = "{\"rejectReason\":\"" + "x".repeat(201) + "\"}";

        mockMvc.perform(post("/api/admin/leave-requests/10/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(currentAuthorityService).requireAuthenticatedAdmin(EMPLOYEE_ID);
        verifyNoInteractions(leaveApprovalService);
    }
}
