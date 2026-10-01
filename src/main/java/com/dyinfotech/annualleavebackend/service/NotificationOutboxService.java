package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.domain.NotificationOutbox;
import com.dyinfotech.annualleavebackend.domain.NotificationOutbox.Status;
import com.dyinfotech.annualleavebackend.repository.NotificationOutboxRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    private static final int MAX_BATCH = 100;
    private static final int STALE_CLAIM_MINUTES = 5;

    private final NotificationOutboxRepository repository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueTeams(Collection<Long> approverIds, String title, String body) {
        if (approverIds == null || approverIds.isEmpty()) {
            return;
        }

        String encodedIds = approverIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (encodedIds.isBlank()) {
            return;
        }

        NotificationOutbox outbox = new NotificationOutbox(
                encodedIds,
                title,
                body,
                LocalDateTime.now(clock));
        repository.save(outbox);

        // 실제 발송은 commit 이후 worker가 시작한다. rollback된 휴가 신청은 발송하지 않는다.
        eventPublisher.publishEvent(new NotificationEnqueued(outbox.getOutboxId()));
    }

    @Transactional(readOnly = true)
    public List<Long> findReadyIds(int limit) {
        int bounded = Math.max(1, Math.min(limit, MAX_BATCH));
        return repository.findReadyIds(
                Status.PENDING,
                LocalDateTime.now(clock),
                PageRequest.of(0, bounded));
    }

    @Transactional
    public Optional<ClaimedNotification> claim(Long outboxId) {
        NotificationOutbox outbox = repository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Set<Long> approverIds;
        try {
            approverIds = parseApproverIds(outbox.getApproverIds());
        } catch (RuntimeException e) {
            // 손상된 payload가 매 poll마다 가장 앞에서 worker 전체를 막지 않도록 영구 격리한다.
            outbox.markDead(now, "invalid approver_ids: " + e.getMessage());
            return Optional.empty();
        }

        if (!outbox.claim(now)) {
            return Optional.empty();
        }

        return Optional.of(new ClaimedNotification(
                outbox.getOutboxId(),
                approverIds,
                outbox.getTitle(),
                outbox.getBody()));
    }

    private Set<Long> parseApproverIds(String encodedIds) {
        if (encodedIds == null || encodedIds.isBlank()) {
            throw new IllegalArgumentException("approver_ids is empty");
        }

        Set<Long> approverIds = new LinkedHashSet<>();
        for (String value : encodedIds.split(",")) {
            if (value.isBlank()) {
                continue;
            }
            long employeeId = Long.parseLong(value.trim());
            if (employeeId <= 0) {
                throw new IllegalArgumentException("employee id must be positive");
            }
            approverIds.add(employeeId);
        }
        if (approverIds.isEmpty()) {
            throw new IllegalArgumentException("approver_ids has no valid employee id");
        }
        return approverIds;
    }

    @Transactional
    public void markSent(Long outboxId) {
        repository.findByIdForUpdate(outboxId)
                .ifPresent(outbox -> outbox.markSent(LocalDateTime.now(clock)));
    }

    @Transactional
    public Optional<Duration> markFailed(Long outboxId, String error) {
        NotificationOutbox outbox = repository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null || outbox.getStatus() != Status.PROCESSING) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        outbox.markFailed(now, error);
        if (outbox.getStatus() != Status.PENDING) {
            return Optional.empty();
        }

        return Optional.of(Duration.between(now, outbox.getNextAttemptAt()));
    }

    @Transactional
    public void recoverStaleClaims() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> staleIds = repository.findStaleProcessingIds(
                Status.PROCESSING,
                now.minusMinutes(STALE_CLAIM_MINUTES),
                PageRequest.of(0, MAX_BATCH));

        for (Long outboxId : staleIds) {
            repository.findByIdForUpdate(outboxId)
                    .ifPresent(outbox -> {
                        if (outbox.getStatus() == Status.PROCESSING
                                && outbox.getClaimedAt() != null
                                && outbox.getClaimedAt().isBefore(now.minusMinutes(STALE_CLAIM_MINUTES))) {
                            outbox.recoverStaleClaim(now);
                        }
                    });
        }
    }

    public record NotificationEnqueued(Long outboxId) {
    }

    public record ClaimedNotification(
            Long outboxId,
            Set<Long> approverIds,
            String title,
            String body) {
    }
}
