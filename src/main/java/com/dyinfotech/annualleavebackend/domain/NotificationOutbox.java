package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox {

    public enum Status {
        PENDING,
        PROCESSING,
        SENT,
        DEAD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    @Lob
    @Column(name = "approver_ids", nullable = false)
    private String approverIds;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public NotificationOutbox(
            String approverIds,
            String title,
            String body,
            LocalDateTime now) {
        this.approverIds = approverIds;
        this.title = title;
        this.body = body;
        this.status = Status.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public boolean claim(LocalDateTime now) {
        if (status != Status.PENDING || nextAttemptAt.isAfter(now)) {
            return false;
        }
        status = Status.PROCESSING;
        claimedAt = now;
        return true;
    }

    public void markSent(LocalDateTime now) {
        if (status != Status.PROCESSING) {
            return;
        }
        status = Status.SENT;
        sentAt = now;
        claimedAt = null;
        lastError = null;
    }

    public void markFailed(LocalDateTime now, String error) {
        if (status != Status.PROCESSING) {
            return;
        }

        attemptCount++;
        claimedAt = null;
        lastError = truncate(error);

        if (attemptCount >= 10) {
            status = Status.DEAD;
            nextAttemptAt = now;
            return;
        }

        status = Status.PENDING;
        long delaySeconds = Math.min(900L, 5L << Math.min(attemptCount - 1, 7));
        nextAttemptAt = now.plusSeconds(delaySeconds);
    }

    public void recoverStaleClaim(LocalDateTime now) {
        if (status == Status.PROCESSING) {
            status = Status.PENDING;
            claimedAt = null;
            nextAttemptAt = now;
            lastError = "stale processing claim recovered";
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
