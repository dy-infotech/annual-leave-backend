package com.dyinfotech.annualleavebackend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.dyinfotech.annualleavebackend.controller.AuthController;
import com.dyinfotech.annualleavebackend.service.AuthService;
import com.dyinfotech.annualleavebackend.service.PasswordResetService;
import com.dyinfotech.annualleavebackend.service.RefreshTokenCookieService;
import com.dyinfotech.annualleavebackend.service.RefreshTokenService;

class AuthSessionMarkerRegressionTest {

    @Test
    void sessionMarker_withoutRefreshCookie_returnsNoContent() {
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

        var response = controller.sessionMarker(request);

        assertEquals(204, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertNull(response.getBody());
        verifyNoInteractions(refreshTokenService);
    }
}
