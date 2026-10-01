package com.dyinfotech.annualleavebackend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.controller.AuthController;
import com.dyinfotech.annualleavebackend.dto.SignInDto;
import com.dyinfotech.annualleavebackend.service.AuthService;
import com.dyinfotech.annualleavebackend.service.PasswordResetService;
import com.dyinfotech.annualleavebackend.service.RefreshTokenCookieService;
import com.dyinfotech.annualleavebackend.service.RefreshTokenService;

class AuthSessionMarkerRegressionTest {

    @Test
    void sessionMarker_withoutRefreshCookie_returnsUnauthorized() {
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
        when(refreshTokenCookieService.read(request)).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.sessionMarker(request));

        assertEquals(401, error.getStatusCode().value());
        assertEquals("refresh token이 없습니다.", error.getReason());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void refresh_withoutSessionMarker_usesResourceManagementCompatibleFallback() {
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
        MockHttpServletResponse response = new MockHttpServletResponse();

        SignInDto.SignInResponse access = SignInDto.SignInResponse.builder()
                .token("access-token")
                .employeeId(10L)
                .name("tester")
                .role("EMPLOYEE")
                .email("tester@example.com")
                .build();
        RefreshTokenService.IssuedRefreshToken issued =
                new RefreshTokenService.IssuedRefreshToken(
                        "next-refresh-token",
                        Instant.now().plusSeconds(3600),
                        "session-a");
        RefreshTokenService.RefreshResult refreshResult =
                new RefreshTokenService.RefreshResult(access, issued);

        when(refreshTokenCookieService.read(request)).thenReturn("refresh-token");
        when(refreshTokenService.rotate("refresh-token")).thenReturn(refreshResult);
        when(refreshTokenCookieService.issue(issued))
                .thenReturn(ResponseCookie.from("refresh", "next-refresh-token")
                        .httpOnly(true)
                        .path("/")
                        .build());

        var result = controller.refresh(request, response);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("session-a", result.getBody().getSsoSessionMarker());
        verify(refreshTokenService).rotate("refresh-token");
    }
}
