package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.dyinfotech.annualleavebackend.domain.NotificationOutbox;
import com.dyinfotech.annualleavebackend.domain.NotificationOutbox.Status;
import com.dyinfotech.annualleavebackend.domain.QNotificationOutbox;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NotificationOutboxRepositoryImpl
        implements NotificationOutboxRepositoryCustom {

    private static final QNotificationOutbox qOutbox =
            QNotificationOutbox.notificationOutbox;

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Long> findReadyIds(
            Status status,
            LocalDateTime now,
            Pageable pageable) {
        return queryFactory
                .select(qOutbox.outboxId)
                .from(qOutbox)
                .where(
                        qOutbox.status.eq(status),
                        qOutbox.nextAttemptAt.loe(now))
                .orderBy(qOutbox.outboxId.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    @Override
    public List<Long> findStaleProcessingIds(
            Status status,
            LocalDateTime staleBefore,
            Pageable pageable) {
        return queryFactory
                .select(qOutbox.outboxId)
                .from(qOutbox)
                .where(
                        qOutbox.status.eq(status),
                        qOutbox.claimedAt.lt(staleBefore))
                .orderBy(qOutbox.outboxId.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    @Override
    public Optional<NotificationOutbox> findByIdForUpdate(Long outboxId) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(qOutbox)
                        .where(qOutbox.outboxId.eq(outboxId))
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .fetchOne());
    }
}
