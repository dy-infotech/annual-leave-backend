package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.domain.NotificationOutbox;
import com.dyinfotech.annualleavebackend.domain.NotificationOutbox.Status;

import jakarta.persistence.LockModeType;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Query("""
            select outbox.outboxId
              from NotificationOutbox outbox
             where outbox.status = :status
               and outbox.nextAttemptAt <= :now
             order by outbox.outboxId
            """)
    List<Long> findReadyIds(
            @Param("status") Status status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("""
            select outbox.outboxId
              from NotificationOutbox outbox
             where outbox.status = :status
               and outbox.claimedAt < :staleBefore
             order by outbox.outboxId
            """)
    List<Long> findStaleProcessingIds(
            @Param("status") Status status,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select outbox from NotificationOutbox outbox where outbox.outboxId = :outboxId")
    Optional<NotificationOutbox> findByIdForUpdate(@Param("outboxId") Long outboxId);
}
