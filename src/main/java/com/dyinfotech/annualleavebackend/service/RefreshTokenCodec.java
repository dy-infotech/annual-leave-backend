package com.dyinfotech.annualleavebackend.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.config.AuthTokenProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefreshTokenCodec {
    public record ParsedToken(String sessionId, int generation, String tokenHash) {}

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";

    private final AuthTokenProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(String sessionId, int generation) {
        if (generation < 0) {
            throw new IllegalArgumentException("refresh generation은 음수일 수 없습니다.");
        }
        properties.validate();

        byte[] secretBytes = new byte[properties.getRefreshTokenBytes()];
        secureRandom.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String payload = sessionId + "." + generation + "." + secret;
        return payload + "." + hmac(payload);
    }

    public Optional<ParsedToken> parse(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        String[] parts = token.split("\\.", -1);
        if (parts.length != 4 || parts[2].isBlank() || parts[3].isBlank()) {
            return Optional.empty();
        }

        try {
            UUID.fromString(parts[0]);
            int generation = Integer.parseInt(parts[1]);
            if (generation < 0) return Optional.empty();

            String payload = parts[0] + "." + parts[1] + "." + parts[2];
            if (!constantEquals(hmac(payload), parts[3])) {
                return Optional.empty();
            }

            return Optional.of(new ParsedToken(parts[0], generation, hash(token)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.getRefreshSigningSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Refresh token HMAC을 사용할 수 없습니다.", e);
        }
    }

    private static boolean constantEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }
}
