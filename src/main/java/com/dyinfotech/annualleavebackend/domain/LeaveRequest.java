package com.dyinfotech.annualleavebackend.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.support.CreatedAudit;
import com.dyinfotech.annualleavebackend.domain.support.HasCreatedAudit;
import com.dyinfotech.annualleavebackend.domain.support.IpEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@EntityListeners({AuditingEntityListener.class, IpEntityListener.class})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest implements HasCreatedAudit {

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

    @Column(name = "managed_ip", nullable = false, updatable = false, length = 45)
    private String managedIp;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;
    
    @Embedded
    private CreatedAudit createdAudit = new CreatedAudit();

    @Builder
    public LeaveRequest(Employee employee, String leaveType, LocalDate startDate, LocalDate endDate, Float useDays, String leaveReason) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.useDays = useDays;
        this.leaveReason = leaveReason;
        this.status = LeaveRequestStatus.PENDING;
    }

    public void cancel(Clock clock) {
        if (this.status != LeaveRequestStatus.PENDING && this.status != LeaveRequestStatus.APPROVED) {
        	String errorMsg = "대기 또는 승인 상태인 신청만 취소할 수 있습니다.";
        	String detailMsg = "requestId : " + requestId + ",status: " + status;
        	log.error(errorMsg + " " + detailMsg);
            throw new IllegalStateException(errorMsg);
        }

        // 이미 휴가가 시작된(또는 지나간) 건은 취소 불가 (신청한 건에 대해서는 기간 체크 안 하는 걸로 수정)
        if (this.status == LeaveRequestStatus.APPROVED && !this.startDate.isAfter(LocalDate.now(clock))) {
        	String errorMsg = "이미 시작되었거나 지난 휴가는 취소할 수 없습니다.";
        	String detailMsg = "requestId : " + requestId + ",startDate: " + startDate + ",currDate: " + LocalDate.now(clock);
        	log.error(errorMsg + " " + detailMsg);
            throw new IllegalStateException(errorMsg);
        }

        this.status = LeaveRequestStatus.CANCELLED;
    }

}
