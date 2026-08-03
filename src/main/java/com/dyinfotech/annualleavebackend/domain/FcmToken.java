package com.dyinfotech.annualleavebackend.domain;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.dyinfotech.annualleavebackend.domain.support.HasUpdatedAudit;
import com.dyinfotech.annualleavebackend.domain.support.IpEntityListener;
import com.dyinfotech.annualleavebackend.domain.support.UpdatedAudit;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fcm_token")
@Getter
@EntityListeners({AuditingEntityListener.class, IpEntityListener.class})
@NoArgsConstructor
public class FcmToken implements HasUpdatedAudit {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @Column(name = "employee_id")
    private Long employeeId;

    // UNIQUE 제약조건으로 기기 변경 시 덮어쓰기 대응
    @Column(name = "fcm_token", nullable = false, unique = true)
    private String token;

    @Column(name = "device_os")
    private String deviceOs;
    
    @Embedded
    private UpdatedAudit updatedAudit = new UpdatedAudit();

    public FcmToken(Long employeeId, String token, String deviceOs) {
        this.employeeId = employeeId;
        this.token = token;
        this.deviceOs = deviceOs;
    }

	public void setEmployeeId(Long employeeId) {
		this.employeeId = employeeId;
	}

	public void setDeviceOs(String deviceOs) {
		this.deviceOs = deviceOs;
	}
}
