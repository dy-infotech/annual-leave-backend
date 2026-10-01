package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.dyinfotech.annualleavebackend.domain.NotificationOutbox;
import com.dyinfotech.annualleavebackend.domain.NotificationOutbox.Status;
import com.dyinfotech.annualleavebackend.repository.NotificationOutboxRepository;

class NotificationOutboxServiceRegressionTest {

    @Test
    void malformedApproverIds_areQuarantinedAsDead() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-01T00:00:00Z"),
                ZoneOffset.UTC);
        NotificationOutboxService service =
                new NotificationOutboxService(repository, clock);

        NotificationOutbox outbox = new NotificationOutbox(
                "100,not-a-number,200",
                "title",
                "body",
                java.time.LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(outbox));

        var claimed = service.claim(10L);

        assertTrue(claimed.isEmpty());
        assertEquals(Status.DEAD, outbox.getStatus());
        assertTrue(outbox.getLastError().startsWith("invalid approver_ids:"));
    }
}
