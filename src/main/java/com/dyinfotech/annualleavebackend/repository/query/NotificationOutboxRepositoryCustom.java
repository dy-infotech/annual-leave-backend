package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

import com.dyinfotech.annualleavebackend.domain.NotificationOutbox;
import com.dyinfotech.annualleavebackend.domain.NotificationOutbox.Status;

public interface NotificationOutboxRepositoryCustom {

    List<Long> findReadyIds(
            Status status,
            LocalDateTime now,
            Pageable pageable);

    List<Long> findStaleProcessingIds(
            Status status,
            LocalDateTime staleBefore,
            Pageable pageable);

    Optional<NotificationOutbox> findByIdForUpdate(Long outboxId);
}
