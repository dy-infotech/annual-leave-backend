package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_request_id")
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // TODO: 반반차 도입시 Decimal(5, 2). 1시간 연차 도입시 Decimal(6, 3)
    @Column(name = "use_days", nullable = false, precision = 4, scale = 1)
    private Float useDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private LeaveRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private Employee approver;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public LeaveRequest(Employee employee, LocalDate startDate, LocalDate endDate, Float useDays) {
        this.employee = employee;
        this.startDate = startDate;
        this.endDate = endDate;
        this.useDays = useDays;
        this.status = LeaveRequestStatus.PENDING;
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

    public void approve(Employee approver) {
        if (this.status != LeaveRequestStatus.PENDING) {
            throw new IllegalStateException("대기 상태인 요청만 승인할 수 있습니다.");
        }

        this.status = LeaveRequestStatus.APPROVED;
        this.approver = approver;
        this.processedAt = LocalDateTime.now();
    }

    public void reject(Employee approver, String rejectReason) {
        if (this.status != LeaveRequestStatus.PENDING) {
            throw new IllegalStateException("대기 상태인 요청만 반려할 수 있습니다.");
        }

        this.status = LeaveRequestStatus.REJECTED;
        this.approver = approver;
        this.rejectReason = rejectReason;
        this.processedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status != LeaveRequestStatus.PENDING && this.status != LeaveRequestStatus.APPROVED) {
            throw new IllegalStateException("대기 또는 승인 상태인 신청만 취소할 수 있습니다.");
        }

        // 이미 휴가가 시작된(또는 지나간) 건은 취소 불가
        if (!this.startDate.isAfter(LocalDate.now())) {
            throw new IllegalStateException("이미 시작되었거나 지난 휴가는 취소할 수 없습니다.");
        }

        this.status = LeaveRequestStatus.CANCELLED;
    }

}
