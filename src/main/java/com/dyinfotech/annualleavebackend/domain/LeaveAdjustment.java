package com.dyinfotech.annualleavebackend.domain;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.dyinfotech.annualleavebackend.common.IpContext;
import com.dyinfotech.annualleavebackend.domain.support.HasCreatedIp;
import com.dyinfotech.annualleavebackend.domain.support.HasUpdatedAudit;
import com.dyinfotech.annualleavebackend.domain.support.IpEntityListener;
import com.dyinfotech.annualleavebackend.domain.support.UpdatedAudit;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "leave_adjustment")
@Getter
@IdClass(LeaveAdjustment.LeaveAdjustmentId.class)
@EntityListeners({AuditingEntityListener.class, IpEntityListener.class})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveAdjustment implements HasCreatedIp, HasUpdatedAudit {

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

    // XXX: createdAt이 PK가 되면 초단위로 동시에 복합키가 일치하는 경우가 생길 수 있다.. 그러나 이 데이터는 그런 경우는 오히려 데이터 생성을 막아야 한다..
    @Id
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "created_ip", nullable = false, updatable = false, length = 45)
    private String createdIp;
    
    @Embedded
    private UpdatedAudit updatedAudit;

    @Builder
    public LeaveAdjustment(Long employeeId, String year, String sign, Float leaveDays, String reason, Clock clock) {
        this.employeeId = employeeId;
        this.year = year;
        this.sign = sign;
        this.leaveDays = leaveDays;
        this.reason = reason;
        this.createdAt = LocalDateTime.now(clock);
        this.createdIp = IpContext.get();
    }
    
    @Override
    public void changeCreatedIp(String ip) {
        this.createdIp = ip;
    }

    // Composite id defined as a static inner class so LeaveAdjustment is self-contained.
    @Getter
    public static class LeaveAdjustmentId implements java.io.Serializable {
        /**
		 * default serialVersionUID for Serializable
		 */
		private static final long serialVersionUID = 893589495980515486L;
		
		private Long employeeId;
        private String year;
        private LocalDateTime createdAt;

        public LeaveAdjustmentId() {
        }

        public LeaveAdjustmentId(Long employeeId, String year, LocalDateTime createdAt) {
            this.employeeId = employeeId;
            this.year = year;
            this.createdAt = createdAt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LeaveAdjustmentId that = (LeaveAdjustmentId) o;
            return java.util.Objects.equals(employeeId, that.employeeId) && 
            		java.util.Objects.equals(year, that.year) && 
            		java.util.Objects.equals(createdAt, that.createdAt);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(employeeId, year, createdAt);
        }
    }
    
}
