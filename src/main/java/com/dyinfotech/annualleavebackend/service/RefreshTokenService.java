package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.config.AuthTokenProperties;
import com.dyinfotech.annualleavebackend.domain.RefreshTokenSession;
import com.dyinfotech.annualleavebackend.dto.SignInDto;
import com.dyinfotech.annualleavebackend.repository.RefreshTokenSessionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    public record IssuedRefreshToken(String token, Instant expiresAt, String sessionMarker) {}
    public record RefreshResult(SignInDto.SignInResponse access, IssuedRefreshToken refresh) {}

    private final RefreshTokenSessionRepository repository;
    private final AuthTokenProperties properties;
    private final RefreshTokenCodec codec;
    private final AuthService authService;
    private final Clock clock;

    @Transactional
    public IssuedRefreshToken issue(Long employeeId) {
        properties.validate();

        LocalDateTime now = nowUtc();
        LocalDateTime absolute = now.plus(properties.getRefreshAbsoluteTtl());
        LocalDateTime idle = min(now.plus(properties.getRefreshIdleTtl()), absolute);

        String sessionId = UUID.randomUUID().toString();
        String token = codec.issue(sessionId, 0);
        repository.save(new RefreshTokenSession(
                sessionId,
                employeeId,
                codec.hash(token),
                now,
                idle,
                absolute));

        return new IssuedRefreshToken(token, toInstant(idle), codec.sessionMarker(sessionId));
    }

    @Transactional(noRollbackFor = {RefreshRejectedException.class, RefreshAlreadyRotatedException.class})
    public RefreshResult rotate(String presentedToken) {
        return rotateInternal(presentedToken, null, false);
    }

    @Transactional(noRollbackFor = {RefreshRejectedException.class, RefreshAlreadyRotatedException.class})
    public RefreshResult rotate(
            String presentedToken,
            String expectedSessionMarker) {
        if (expectedSessionMarker == null || expectedSessionMarker.isBlank()) {
            throw new RefreshSessionMismatchException();
        }
        return rotateInternal(presentedToken, expectedSessionMarker, true);
    }

    private RefreshResult rotateInternal(
            String presentedToken,
            String expectedSessionMarker,
            boolean requireSessionMarker) {
        RefreshTokenCodec.ParsedToken parsed = codec.parse(presentedToken)
                .orElseThrow(() -> new RefreshRejectedException("유효하지 않은 refresh token입니다."));

        if (requireSessionMarker
                && !constantEquals(
                        codec.sessionMarker(parsed.sessionId()),
                        expectedSessionMarker)) {
            throw new RefreshSessionMismatchException();
        }

        RefreshTokenSession session = repository.findByIdForUpdate(parsed.sessionId())
                .orElseThrow(() -> new RefreshRejectedException("유효하지 않은 refresh token입니다."));

        LocalDateTime now = nowUtc();
        if (session.isRevoked()) {
            throw new RefreshRejectedException("폐기된 refresh session입니다. 다시 로그인해주세요.");
        }
        if (session.isExpired(now)) {
            session.revoke(now, "EXPIRED");
            throw new RefreshRejectedException("refresh session이 만료되었습니다. 다시 로그인해주세요.");
        }

        int currentGeneration = session.getRotationCount();
        if (parsed.generation() > currentGeneration) {
            throw new RefreshRejectedException("유효하지 않은 refresh token generation입니다.");
        }

        if (parsed.generation() < currentGeneration) {
            if (parsed.generation() == currentGeneration - 1
                    && constantEquals(session.getPreviousTokenHash(), parsed.tokenHash())
                    && session.getPreviousValidUntil() != null
                    && now.isBefore(session.getPreviousValidUntil())) {
                String currentToken = codec.issue(session.getSessionId(), currentGeneration);
                if (!constantEquals(session.getTokenHash(), codec.hash(currentToken))) {
                    // 배포 전 random-secret 방식으로 회전된 세션은 현재 raw token을
                    // 재구성할 수 없으므로 기존 409 계약으로 안전하게 fallback한다.
                    throw new RefreshAlreadyRotatedException();
                }

                SignInDto.SignInResponse replayAccess;
                try {
                    replayAccess = authService.issueCurrentAccessToken(session.getEmployeeId());
                } catch (ResponseStatusException e) {
                    session.revoke(now, "SUBJECT_INVALID");
                    throw new RefreshRejectedException(
                            "현재 사용자 상태로 인증을 갱신할 수 없습니다.");
                }

                return new RefreshResult(
                        replayAccess,
                        new IssuedRefreshToken(
                                currentToken,
                                toInstant(session.getIdleExpiresAt()),
                                codec.sessionMarker(session.getSessionId())));
            }

            session.revoke(now, "REUSE_DETECTED");
            throw new RefreshRejectedException("refresh token 재사용이 감지되어 세션을 종료했습니다.");
        }

        if (!constantEquals(session.getTokenHash(), parsed.tokenHash())) {
            throw new RefreshRejectedException("유효하지 않은 refresh token입니다.");
        }

        SignInDto.SignInResponse access;
        try {
            access = authService.issueCurrentAccessToken(session.getEmployeeId());
        } catch (ResponseStatusException e) {
            session.revoke(now, "SUBJECT_INVALID");
            throw new RefreshRejectedException("현재 사용자 상태로 인증을 갱신할 수 없습니다.");
        }

        int nextGeneration = currentGeneration + 1;
        String nextToken = codec.issue(session.getSessionId(), nextGeneration);
        LocalDateTime nextIdle = min(
                now.plus(properties.getRefreshIdleTtl()),
                session.getAbsoluteExpiresAt());

        session.rotate(
                codec.hash(nextToken),
                now,
                nextIdle,
                now.plus(properties.getRefreshReplayGrace()));

        return new RefreshResult(
                access,
                new IssuedRefreshToken(
                        nextToken,
                        toInstant(nextIdle),
                        codec.sessionMarker(session.getSessionId())));
    }

    @Transactional(readOnly = true)
    public String currentSessionMarker(String presentedToken) {
        RefreshTokenCodec.ParsedToken parsed = codec.parse(presentedToken)
                .orElseThrow(() -> new RefreshRejectedException("유효하지 않은 refresh token입니다."));
        RefreshTokenSession session = repository.findById(parsed.sessionId())
                .orElseThrow(() -> new RefreshRejectedException("유효하지 않은 refresh token입니다."));

        LocalDateTime now = nowUtc();
        if (session.isRevoked() || session.isExpired(now)) {
            throw new RefreshRejectedException("현재 사용할 수 없는 refresh session입니다.");
        }

        int currentGeneration = session.getRotationCount();
        boolean current = parsed.generation() == currentGeneration
                && constantEquals(session.getTokenHash(), parsed.tokenHash());
        boolean previous = parsed.generation() == currentGeneration - 1
                && constantEquals(session.getPreviousTokenHash(), parsed.tokenHash())
                && session.getPreviousValidUntil() != null
                && now.isBefore(session.getPreviousValidUntil());

        if (!current && !previous) {
            throw new RefreshRejectedException("유효하지 않은 refresh token입니다.");
        }
        return codec.sessionMarker(session.getSessionId());
    }

    @Transactional
    public Long revoke(String presentedToken) {
        return revokeInternal(presentedToken, null, false);
    }

    @Transactional
    public Long revokeIfSessionMarker(
            String presentedToken,
            String expectedSessionMarker) {
        if (expectedSessionMarker == null || expectedSessionMarker.isBlank()) {
            return null;
        }
        return revokeInternal(presentedToken, expectedSessionMarker, true);
    }

    private Long revokeInternal(
            String presentedToken,
            String expectedSessionMarker,
            boolean requireSessionMarker) {
        RefreshTokenCodec.ParsedToken parsed = codec.parse(presentedToken).orElse(null);
        if (parsed == null) return null;

        if (requireSessionMarker
                && !constantEquals(
                        codec.sessionMarker(parsed.sessionId()),
                        expectedSessionMarker)) {
            return null;
        }

        RefreshTokenSession session = repository.findByIdForUpdate(parsed.sessionId()).orElse(null);
        if (session == null) return null;

        if (!session.isRevoked() && parsed.generation() <= session.getRotationCount()) {
            session.revoke(nowUtc(), "LOGOUT");
        }
        return session.getEmployeeId();
    }

    @Scheduled(cron = "${app.auth.refresh-cleanup-cron:0 15 4 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void cleanupTerminalSessions() {
        repository.deleteTerminalBefore(
                nowUtc().minus(properties.getRefreshSessionRetention()));
    }

    private static boolean constantEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant toInstant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    private static class RefreshRejectedException extends ResponseStatusException {
        private RefreshRejectedException(String reason) {
            super(HttpStatus.UNAUTHORIZED, reason);
        }
    }

    private static final class RefreshSessionMismatchException extends ResponseStatusException {
        private RefreshSessionMismatchException() {
            super(HttpStatus.CONFLICT,
                    "현재 access session과 refresh session이 일치하지 않습니다.");
        }
    }

    private static final class RefreshAlreadyRotatedException extends ResponseStatusException {
        private RefreshAlreadyRotatedException() {
            super(HttpStatus.CONFLICT,
                    "refresh token이 이미 회전되었습니다. 갱신된 cookie로 다시 시도해주세요.");
        }
    }
}
