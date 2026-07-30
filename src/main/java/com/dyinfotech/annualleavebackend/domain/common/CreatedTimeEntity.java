package com.dyinfotech.annualleavebackend.domain.common;

import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class CreatedTimeEntity {
	// created_at         DATETIME		NOT NULL DEFAULT CURRENT_TIMESTAMP 일 때만 사용
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
