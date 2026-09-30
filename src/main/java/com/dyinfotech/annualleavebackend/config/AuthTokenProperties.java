package com.dyinfotech.annualleavebackend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthTokenProperties {
    private Duration refreshIdleTtl = Duration.ofDays(7);
    private Duration refreshAbsoluteTtl = Duration.ofDays(30);
    private Duration refreshReplayGrace = Duration.ofSeconds(5);
    private Duration refreshSessionRetention = Duration.ofDays(7);
    private int refreshTokenBytes = 32;
    private String refreshSigningSecret;
    private String refreshCookieName = "DY_SSO_REFRESH";
    private String refreshCookiePath = "/";
    private boolean refreshCookieSecure = true;
    private String refreshCookieSameSite = "Strict";

    public Duration getRefreshIdleTtl() { return refreshIdleTtl; }
    public void setRefreshIdleTtl(Duration value) { refreshIdleTtl = positive(value, "refresh-idle-ttl"); }
    public Duration getRefreshAbsoluteTtl() { return refreshAbsoluteTtl; }
    public void setRefreshAbsoluteTtl(Duration value) { refreshAbsoluteTtl = positive(value, "refresh-absolute-ttl"); }
    public Duration getRefreshReplayGrace() { return refreshReplayGrace; }
    public void setRefreshReplayGrace(Duration value) { refreshReplayGrace = positive(value, "refresh-replay-grace"); }
    public Duration getRefreshSessionRetention() { return refreshSessionRetention; }
    public void setRefreshSessionRetention(Duration value) { refreshSessionRetention = positive(value, "refresh-session-retention"); }
    public int getRefreshTokenBytes() { return refreshTokenBytes; }
    public void setRefreshTokenBytes(int value) {
        if (value < 32) throw new IllegalArgumentException("app.auth.refresh-token-bytes는 32 이상이어야 합니다.");
        refreshTokenBytes = value;
    }
    public String getRefreshSigningSecret() { return refreshSigningSecret; }
    public void setRefreshSigningSecret(String value) { refreshSigningSecret = text(value, "refresh-signing-secret"); }
    public String getRefreshCookieName() { return refreshCookieName; }
    public void setRefreshCookieName(String value) { refreshCookieName = text(value, "refresh-cookie-name"); }
    public String getRefreshCookiePath() { return refreshCookiePath; }
    public void setRefreshCookiePath(String value) { refreshCookiePath = text(value, "refresh-cookie-path"); }
    public boolean isRefreshCookieSecure() { return refreshCookieSecure; }
    public void setRefreshCookieSecure(boolean value) { refreshCookieSecure = value; }
    public String getRefreshCookieSameSite() { return refreshCookieSameSite; }
    public void setRefreshCookieSameSite(String value) {
        String normalized = text(value, "refresh-cookie-same-site");
        if (!normalized.equalsIgnoreCase("Strict")
                && !normalized.equalsIgnoreCase("Lax")
                && !normalized.equalsIgnoreCase("None")) {
            throw new IllegalArgumentException("app.auth.refresh-cookie-same-site는 Strict/Lax/None 중 하나여야 합니다.");
        }
        refreshCookieSameSite = normalized;
    }

    public void validate() {
        if (refreshSigningSecret == null
                || refreshSigningSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("refresh signing secret은 최소 32 bytes여야 합니다.");
        }
        if (refreshAbsoluteTtl.compareTo(refreshIdleTtl) < 0) {
            throw new IllegalStateException("refresh absolute TTL은 idle TTL보다 짧을 수 없습니다.");
        }
        if (refreshCookieSameSite.equalsIgnoreCase("None") && !refreshCookieSecure) {
            throw new IllegalStateException("SameSite=None refresh cookie는 Secure가 필요합니다.");
        }
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("app.auth." + name + "은 0보다 커야 합니다.");
        }
        return value;
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("app.auth." + name + "은 비어 있을 수 없습니다.");
        }
        return value.trim();
    }
}
