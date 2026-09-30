package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.config.AuthTokenProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookieService {
    private final AuthTokenProperties properties;
    private final Clock clock;

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (properties.getRefreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public ResponseCookie issue(RefreshTokenService.IssuedRefreshToken token) {
        Duration remaining = Duration.between(clock.instant(), token.expiresAt());
        if (remaining.isNegative()) remaining = Duration.ZERO;
        return base(token.token()).maxAge(remaining).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(properties.isRefreshCookieSecure())
                .sameSite(properties.getRefreshCookieSameSite())
                .path(properties.getRefreshCookiePath());
    }
}
