package com.dyinfotech.annualleavebackend.scheduler;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.dyinfotech.annualleavebackend.config.TimeConfig;
import com.dyinfotech.annualleavebackend.service.FcmService;
import com.dyinfotech.annualleavebackend.service.NotificationOutboxService;
import com.dyinfotech.annualleavebackend.service.NotificationOutboxService.ClaimedNotification;
import com.dyinfotech.annualleavebackend.service.NotificationOutboxService.NotificationEnqueued;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxWorker {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_DRAIN_BATCHES = 10;
    private static final int MAX_PENDING_WORKER_TASKS = 1000;

    private final NotificationOutboxService outboxService;
    private final FcmService fcmService;
    private final ScheduledExecutorService retryExecutor;

    private final AtomicInteger pendingWorkerTasks = new AtomicInteger();

    /**
     * 정상 경로는 DB polling이 아니라 enqueue transaction의 AFTER_COMMIT 이벤트로 깨운다.
     * 이 sweep은 프로세스 종료/이벤트 제출 실패/PROCESSING 고착을 복구하기 위한 safety net이다.
     */
    @Scheduled(
            fixedDelayString = "${notification.outbox.recovery-delay-ms:300000}",
            initialDelayString = "${notification.outbox.initial-delay-ms:5000}",
            zone = TimeConfig.TIME_ZONE)
    public void processPending() {
        outboxService.recoverStaleClaims();
        drainReady();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationEnqueued(NotificationEnqueued event) {
        if (event.outboxId() == null) {
            scheduleWorker(this::drainReady, Duration.ZERO, "enqueue-without-id");
            return;
        }
        scheduleWorker(() -> processOne(event.outboxId()), Duration.ZERO, "enqueue");
    }

    private void drainReady() {
        for (int batch = 0; batch < MAX_DRAIN_BATCHES; batch++) {
            List<Long> readyIds = outboxService.findReadyIds(BATCH_SIZE);
            if (readyIds.isEmpty()) {
                return;
            }

            for (Long outboxId : readyIds) {
                processOne(outboxId);
            }

            if (readyIds.size() < BATCH_SIZE) {
                return;
            }
        }

        log.warn("notification outbox recovery sweep batch limit reached. maxRows={}",
                BATCH_SIZE * MAX_DRAIN_BATCHES);
    }

    private void processOne(Long outboxId) {
        try {
            ClaimedNotification notification = outboxService.claim(outboxId).orElse(null);
            if (notification == null) {
                return;
            }

            boolean success = fcmService.sendConditionNotificationNowAndReport(
                    notification.approverIds(),
                    notification.title(),
                    notification.body());

            if (success) {
                outboxService.markSent(notification.outboxId());
                return;
            }

            recordFailureAndScheduleRetry(
                    notification.outboxId(),
                    "FCM send returned partial/total failure");
        } catch (RuntimeException e) {
            // 한 row의 payload/DB 문제가 같은 sweep의 뒤쪽 정상 알림까지 막지 않게 격리한다.
            log.error("notification outbox 처리 실패. outboxId={}", outboxId, e);
            recordFailureAndScheduleRetry(outboxId, e.toString());
        }
    }

    private void recordFailureAndScheduleRetry(Long outboxId, String error) {
        try {
            Optional<Duration> retryDelay = outboxService.markFailed(outboxId, error);
            retryDelay.ifPresent(delay ->
                    scheduleWorker(() -> processOne(outboxId), delay, "retry"));
        } catch (RuntimeException markError) {
            log.error("notification outbox 실패 상태 기록도 실패. outboxId={}", outboxId, markError);
        }
    }

    private void scheduleWorker(Runnable task, Duration delay, String reason) {
        int pending = pendingWorkerTasks.incrementAndGet();
        if (pending > MAX_PENDING_WORKER_TASKS) {
            pendingWorkerTasks.decrementAndGet();
            log.warn("notification outbox worker queue limit reached. reason={}, limit={}",
                    reason, MAX_PENDING_WORKER_TASKS);
            return;
        }

        long delayMillis = Math.max(0L, delay.toMillis());
        try {
            retryExecutor.schedule(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.error("notification outbox scheduled task failed. reason={}", reason, e);
                } finally {
                    pendingWorkerTasks.decrementAndGet();
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            pendingWorkerTasks.decrementAndGet();
            log.error("notification outbox task scheduling failed. reason={}", reason, e);
        }
    }
}
