package com.dyinfotech.annualleavebackend.domain.support;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class CreatedAudit {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_ip", nullable = false, updatable = false, length = 45)
    private String createdIp;

    void setCreatedIp(String ip) { // package-private
        this.createdIp = ip;
    }
}