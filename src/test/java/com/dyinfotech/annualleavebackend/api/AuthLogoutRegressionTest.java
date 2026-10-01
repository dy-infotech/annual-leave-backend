package com.dyinfotech.annualleavebackend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.controller.AuthController;
import com.dyinfotech.annualleavebackend.service.AuthService;
import com.dyinfotech.annualleavebackend.service.PasswordResetService;
import com.dyinfotech.annualleavebackend.service.RefreshTokenCookieService;
import com.dyinfotech.annualleavebackend.service.RefreshTokenService;

class AuthLogoutRegressionTest {

    @Test
    void logout_doesNotWaitForFcmCleanup() {
        AuthService authService = mock(AuthService.class);
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        RefreshTokenCookieService refreshTokenCookieService =
                mock(RefreshTokenCookieService.class);

        AuthController controller = new AuthController(
                authService,
                passwordResetService,
                refreshTokenService,
                refreshTokenCookieService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-SSO-Refresh", "1");

        when(refreshTokenCookieService.read(request)).thenReturn("refresh-token");
        when(refreshTokenService.revoke("refresh-token")).thenReturn(10L);
        when(refreshTokenCookieService.clear())
                .thenReturn(ResponseCookie.from("refresh", "")
                        .httpOnly(true)
                        .path("/")
                        .maxAge(0)
                        .build());

        CompletableFuture<Void> pendingFcmCleanup = new CompletableFuture<>();
        when(authService.logout(10L, null, null)).thenReturn(pendingFcmCleanup);

        var responseFuture = controller.logout(
                request,
                new EmployeePrincipal(10L, Role.EMPLOYEE),
                null);

        assertTrue(responseFuture.isDone());
        assertFalse(pendingFcmCleanup.isDone());

        var response = responseFuture.join();
        assertEquals(204, response.getStatusCode().value());
        assertNotNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

        verify(refreshTokenService).revoke("refresh-token");
        verify(authService).logout(10L, null, null);
    }

    @Test
    void logout_fcmCleanupStartFailureStillReturnsNoContent() {
        AuthService authService = mock(AuthService.class);
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        RefreshTokenCookieService refreshTokenCookieService =
                mock(RefreshTokenCookieService.class);

        AuthController controller = new AuthController(
                authService,
                passwordResetService,
                refreshTokenService,
                refreshTokenCookieService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-SSO-Refresh", "1");

        when(refreshTokenCookieService.read(request)).thenReturn("refresh-token");
        when(refreshTokenService.revoke("refresh-token")).thenReturn(10L);
        when(refreshTokenCookieService.clear())
                .thenReturn(ResponseCookie.from("refresh", "")
                        .httpOnly(true)
                        .path("/")
                        .maxAge(0)
                        .build());
        when(authService.logout(10L, null, null))
                .thenThrow(new IllegalStateException("cleanup unavailable"));

        var response = controller.logout(
                request,
                new EmployeePrincipal(10L, Role.EMPLOYEE),
                null).join();

        assertEquals(204, response.getStatusCode().value());
        verify(refreshTokenService).revoke("refresh-token");
    }
    @Test
    void backgroundLogout_doesNotClearRefreshCookie() {
        AuthService authService = mock(AuthService.class);
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        RefreshTokenCookieService refreshTokenCookieService =
                mock(RefreshTokenCookieService.class);

        AuthController controller = new AuthController(
                authService,
                passwordResetService,
                refreshTokenService,
                refreshTokenCookieService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-SSO-Refresh", "1");
        request.addHeader("X-SSO-Background-Logout", "1");
        request.addHeader("X-SSO-Session-Marker", "old-session");

        when(refreshTokenCookieService.read(request)).thenReturn("old-refresh-token");
        when(refreshTokenService.revokeIfSessionMarker(
                "old-refresh-token", "old-session")).thenReturn(10L);
        when(authService.logout(10L, null, "old-session"))
                .thenReturn(CompletableFuture.completedFuture(null));

        var response = controller.logout(
                request,
                new EmployeePrincipal(10L, Role.EMPLOYEE),
                null).join();

        assertEquals(204, response.getStatusCode().value());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        verify(refreshTokenService).revokeIfSessionMarker(
                "old-refresh-token", "old-session");
        verify(authService).logout(10L, null, "old-session");
    }

}
