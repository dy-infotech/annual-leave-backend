package com.dyinfotech.annualleavebackend.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.dyinfotech.annualleavebackend.scheduler.NotificationOutboxWorker;
import com.dyinfotech.annualleavebackend.service.NotificationOutboxService.ClaimedNotification;

class NotificationOutboxWorkerRegressionTest {

    @Test
    void successfulSend_marksOutboxSent() {
        NotificationOutboxService outboxService = mock(NotificationOutboxService.class);
        FcmService fcmService = mock(FcmService.class);
        NotificationOutboxWorker worker = new NotificationOutboxWorker(outboxService, fcmService);

        ClaimedNotification claimed =
                new ClaimedNotification(10L, Set.of(100L), "title", "body");
        when(outboxService.findReadyIds(50)).thenReturn(List.of(10L));
        when(outboxService.claim(10L)).thenReturn(Optional.of(claimed));
        when(fcmService.sendConditionNotificationNowAndReport(Set.of(100L), "title", "body"))
                .thenReturn(true);

        worker.processPending();

        verify(outboxService).markSent(10L);
    }

    @Test
    void failedSend_keepsOutboxForRetry() {
        NotificationOutboxService outboxService = mock(NotificationOutboxService.class);
        FcmService fcmService = mock(FcmService.class);
        NotificationOutboxWorker worker = new NotificationOutboxWorker(outboxService, fcmService);

        ClaimedNotification claimed =
                new ClaimedNotification(11L, Set.of(101L), "title", "body");
        when(outboxService.findReadyIds(50)).thenReturn(List.of(11L));
        when(outboxService.claim(11L)).thenReturn(Optional.of(claimed));
        when(fcmService.sendConditionNotificationNowAndReport(Set.of(101L), "title", "body"))
                .thenReturn(false);

        worker.processPending();

        verify(outboxService).markFailed(11L, "FCM send returned partial/total failure");
    }
}
