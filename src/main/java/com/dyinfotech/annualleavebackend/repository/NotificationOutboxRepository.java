package com.dyinfotech.annualleavebackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.NotificationOutbox;
import com.dyinfotech.annualleavebackend.repository.query.NotificationOutboxRepositoryCustom;

public interface NotificationOutboxRepository
        extends JpaRepository<NotificationOutbox, Long>, NotificationOutboxRepositoryCustom {
}
