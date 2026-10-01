package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.config.AuthTokenProperties;
import com.dyinfotech.annualleavebackend.domain.RefreshTokenSession;
import com.dyinfotech.annualleavebackend.dto.SignInDto;
import com.dyinfotech.annualleavebackend.repository.RefreshTokenSessionRepository;

class RefreshTokenServiceTest {

    @Test
    void rotatesAndAllowsOnlyImmediatePreviousTokenGrace() {
        Fixture fixture = new Fixture();
        RefreshTokenService.IssuedRefreshToken first = fixture.service.issue(10L);
        RefreshTokenService.RefreshResult second = fixture.service.rotate(first.token());

        RefreshTokenService.RefreshResult duplicate =
                fixture.service.rotate(first.token());
        assertEquals(second.refresh().token(), duplicate.refresh().token());
        assertEquals(1, fixture.stored.get().getRotationCount());
        assertTrue(!fixture.stored.get().isRevoked());

        RefreshTokenService.RefreshResult third = fixture.service.rotate(second.refresh().token());
        assertNotEquals(second.refresh().token(), third.refresh().token());
        assertEquals(2, fixture.stored.get().getRotationCount());

        ResponseStatusException replay = assertThrows(
                ResponseStatusException.class,
                () -> fixture.service.rotate(first.token()));
        assertEquals(HttpStatus.UNAUTHORIZED, replay.getStatusCode());
        assertTrue(fixture.stored.get().isRevoked());
        assertEquals("REUSE_DETECTED", fixture.stored.get().getRevokedReason());
    }

    @Test
    void markerMismatchDoesNotRotateOrRevokeSession() {
        Fixture fixture = new Fixture();
        RefreshTokenService.IssuedRefreshToken issued = fixture.service.issue(10L);

        ResponseStatusException rejected = assertThrows(
                ResponseStatusException.class,
                () -> fixture.service.rotate(issued.token(), "wrong-session-marker"));

        assertEquals(HttpStatus.CONFLICT, rejected.getStatusCode());
        assertEquals(0, fixture.stored.get().getRotationCount());
        assertTrue(!fixture.stored.get().isRevoked());
    }

    @Test
    void invalidMacDoesNotRevokeKnownSession() {
        Fixture fixture = new Fixture();
        RefreshTokenService.IssuedRefreshToken issued = fixture.service.issue(10L);
        String[] parts = issued.token().split("\\.");

        String tampered = parts[0] + "." + parts[1] + "." + parts[2] + ".invalid-mac";
        ResponseStatusException rejected = assertThrows(
                ResponseStatusException.class,
                () -> fixture.service.rotate(tampered));

        assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
        assertTrue(!fixture.stored.get().isRevoked());
    }

    private static final class Fixture {
        private final RefreshTokenSessionRepository repository =
                mock(RefreshTokenSessionRepository.class);
        private final AuthService authService = mock(AuthService.class);
        private final AtomicReference<RefreshTokenSession> stored = new AtomicReference<>();
        private final RefreshTokenService service;

        private Fixture() {
            AuthTokenProperties properties = new AuthTokenProperties();
            properties.setRefreshIdleTtl(Duration.ofDays(7));
            properties.setRefreshAbsoluteTtl(Duration.ofDays(30));
            properties.setRefreshReplayGrace(Duration.ofSeconds(5));
            properties.setRefreshSessionRetention(Duration.ofDays(7));
            properties.setRefreshSigningSecret("test-refresh-signing-secret-0123456789abcdef");

            Clock clock = Clock.fixed(
                    Instant.parse("2026-09-30T00:00:00Z"),
                    ZoneOffset.UTC);
            RefreshTokenCodec codec = new RefreshTokenCodec(properties);
            service = new RefreshTokenService(
                    repository,
                    properties,
                    codec,
                    authService,
                    clock);

            when(repository.save(any(RefreshTokenSession.class))).thenAnswer(invocation -> {
                RefreshTokenSession session = invocation.getArgument(0);
                stored.set(session);
                return session;
            });
            when(repository.findByIdForUpdate(any())).thenAnswer(
                    invocation -> Optional.ofNullable(stored.get()));
            when(authService.issueCurrentAccessToken(10L)).thenReturn(
                    SignInDto.SignInResponse.builder()
                            .token("annual-access")
                            .employeeId(10L)
                            .name("테스트")
                            .role("EMPLOYEE")
                            .email("test@example.com")
                            .build());
        }
    }
}
