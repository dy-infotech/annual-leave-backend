package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;

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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

    @Column(name = "leave_type", nullable = false, length = 50)
    private String leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "use_days", nullable = false)
    private Float useDays;

    @Column(name = "leave_reason", length = 200)
    private String leaveReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private LeaveRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @Column(name = "managed_at")
    private LocalDateTime managedAt;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public LeaveRequest(Employee employee, String leaveType, LocalDate startDate, LocalDate endDate, Float useDays, String leaveReason, Employee manager) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.useDays = useDays;
        this.leaveReason = leaveReason;
        this.status = LeaveRequestStatus.PENDING;
        this.manager = manager;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Deprecated
    public void approve(Employee manager) {
        if (this.status != LeaveRequestStatus.PENDING) {
        	String errorMsg = "대기 상태인 요청만 승인할 수 있습니다.";
        	String detailMsg = "requestId : " + requestId + ",status: " + status;
        	log.error(errorMsg + " " + detailMsg);
            throw new IllegalStateException(errorMsg);
        }

        this.status = LeaveRequestStatus.APPROVED;
        this.manager = manager;
        this.managedAt = LocalDateTime.now();
    }

    @Deprecated
    public void reject(Employee manager, String rejectReason) {
        if (this.status != LeaveRequestStatus.PENDING) {
        	String errorMsg = "대기 상태인 요청만 반려할 수 있습니다.";
        	String detailMsg = "requestId : " + requestId + ",status: " + status;
        	log.error(errorMsg + " " + detailMsg);
            throw new IllegalStateException(errorMsg);
        }

        this.status = LeaveRequestStatus.REJECTED;
        this.manager = manager;
        this.rejectReason = rejectReason;
        this.managedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status != LeaveRequestStatus.PENDING && this.status != LeaveRequestStatus.APPROVED) {
        	String errorMsg = "대기 또는 승인 상태인 신청만 취소할 수 있습니다.";
        	String detailMsg = "requestId : " + requestId + ",status: " + status;
        	log.error(errorMsg + " " + detailMsg);
            throw new IllegalStateException(errorMsg);
        }

        // 이미 휴가가 시작된(또는 지나간) 건은 취소 불가
        if (!this.startDate.isAfter(LocalDate.now())) {
        	String errorMsg = "이미 시작되었거나 지난 휴가는 취소할 수 없습니다.";
        	String detailMsg = "requestId : " + requestId + ",startDate: " + startDate + ",currDate: " + LocalDate.now();
        	log.error(errorMsg + " " + detailMsg);
            throw new IllegalStateException(errorMsg);
        }

        this.status = LeaveRequestStatus.CANCELLED;
    }

}
