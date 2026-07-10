package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDateTime;

import com.dyinfotech.annualleavebackend.domain.BasisData.BasisDataId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "leave_adjustment")
@Getter
@IdClass(LeaveAdjustment.LeaveAdjustmentId.class)
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
    
    @Column(name = "leave_days", nullable = false)
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

    // Composite id defined as a static inner class so BasisData is self-contained.
    public static class LeaveAdjustmentId implements java.io.Serializable {
        private Long employeeId;
        private String year;

        public LeaveAdjustmentId() {
        }

        public LeaveAdjustmentId(Long employeeId, String year) {
            this.employeeId = employeeId;
            this.year = year;
        }

        public Long getEmployeeId() {
            return employeeId;
        }

        public String getYear() {
            return year;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LeaveAdjustment that = (LeaveAdjustment) o;
            return java.util.Objects.equals(employeeId, that.employeeId) && java.util.Objects.equals(year, that.year);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(employeeId, year);
        }
    }
    
}
