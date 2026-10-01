package com.dyinfotech.annualleavebackend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.config.TimeConfig;
import com.dyinfotech.annualleavebackend.service.FcmService;
import com.dyinfotech.annualleavebackend.service.NotificationOutboxService;
import com.dyinfotech.annualleavebackend.service.NotificationOutboxService.ClaimedNotification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxWorker {

    private static final int BATCH_SIZE = 50;

    private final NotificationOutboxService outboxService;
    private final FcmService fcmService;

    @Scheduled(
            fixedDelayString = "${notification.outbox.poll-delay-ms:5000}",
            initialDelayString = "${notification.outbox.initial-delay-ms:5000}",
            zone = TimeConfig.TIME_ZONE)
    public void processPending() {
        outboxService.recoverStaleClaims();

        for (Long outboxId : outboxService.findReadyIds(BATCH_SIZE)) {
            ClaimedNotification notification = outboxService.claim(outboxId).orElse(null);
            if (notification == null) {
                continue;
            }

            try {
                boolean success = fcmService.sendConditionNotificationNowAndReport(
                        notification.approverIds(),
                        notification.title(),
                        notification.body());

                if (success) {
                    outboxService.markSent(notification.outboxId());
                } else {
                    outboxService.markFailed(
                            notification.outboxId(),
                            "FCM send returned partial/total failure");
                }
            } catch (RuntimeException e) {
                log.error("notification outbox 발송 실패. outboxId={}", notification.outboxId(), e);
                outboxService.markFailed(notification.outboxId(), e.toString());
            }
        }
    }
}
