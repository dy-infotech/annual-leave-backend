package com.dyinfotech.annualleavebackend.domain.support;

import java.time.LocalDateTime;

import org.springframework.data.annotation.LastModifiedDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class UpdatedAudit {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_ip", length = 45)
    private String updatedIp;

    void setUpdatedIp(String ip) {
    	this.updatedIp = ip;
    }
}
