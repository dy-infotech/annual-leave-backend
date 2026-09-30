package com.dyinfotech.annualleavebackend.common.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtProvider {

    private static final String CREDENTIAL_VERSION_CLAIM = "credentialVersion";

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long employeeId, String role) {
        return generateToken(employeeId, role, null);
    }

    // 비밀번호 상태를 credentialVersion에 묶어 비밀번호 변경/재설정 즉시 기존 access token을 무효화한다.
    public String generateToken(Long employeeId, String role, String credentialVersion) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(employeeId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)));

        if (credentialVersion != null && !credentialVersion.isBlank()) {
            builder.claim(CREDENTIAL_VERSION_CLAIM, credentialVersion);
        }

        return builder.signWith(secretKey).compact();
    }

    public Long getEmployeeId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public String getCredentialVersion(String token) {
        return parseClaims(token).get(CREDENTIAL_VERSION_CLAIM, String.class);
    }

    /**
     * BCrypt 문자열 자체를 JWT에 노출하지 않고 서버 비밀키로 HMAC한 버전만 claim에 넣는다.
     * 비밀번호 hash가 바뀌면 이 값도 바뀌므로 기존 access token을 즉시 거부할 수 있다.
     */
    public String createCredentialVersion(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            return HexFormat.of().formatHex(mac.doFinal(passwordHash.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JWT credential version을 생성할 수 없습니다.", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 JWT 토큰 요청 차단: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
