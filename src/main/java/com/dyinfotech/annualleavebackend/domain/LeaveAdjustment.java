package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "leave_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveAdjustment {

    @Id
    @Column(name = "employee_id")
    private Long employeeId;

    @Id
    @Column(name = "year", length = 4)
    private String year;

    @Column(name = "sign", length = 5)
    private String sign;
    
    // TODO: 반반차 도입시 Decimal(5, 2). 1시간 연차 도입시 Decimal(6, 3)
    @Column(name = "leave_days", nullable = false, precision = 4, scale = 1)
    private Float leaveDays;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public LeaveAdjustment(Long employeeId, String year, String sign, Float leaveDays, String reason) {
        this.employeeId = employeeId;
        this.year = year;
        this.sign = sign;
        this.leaveDays = leaveDays;
        this.reason = reason;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
}
