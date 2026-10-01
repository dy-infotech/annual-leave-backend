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
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtProvider {

    private static final String CREDENTIAL_VERSION_CLAIM = "credentialVersion";

    private final SecretKey secretKey;
    private final JwtParser jwtParser;
    private final long expirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser().verifyWith(secretKey).build();
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long employeeId, String role) {
        return generateToken(employeeId, role, null);
    }

    // 비밀번호 상태를 토큰 버전에 반영해 변경 시 기존 토큰을 무효화한다
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

    // 비밀번호 해시를 서버 키로 변환해 토큰 버전을 만든다
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

    // 서명과 만료를 검증한 토큰 내용을 반환한다
    public Claims parseVerifiedClaims(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }

    private Claims parseClaims(String token) {
        return parseVerifiedClaims(token);
    }
}
