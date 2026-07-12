package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fcm_token")
@Getter
@NoArgsConstructor
public class FcmToken {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @Column(name = "employee_id")
    private Long employeeId;

    // UNIQUE 제약조건으로 기기 변경 시 덮어쓰기 대응
    @Column(name = "fcm_token", nullable = false, unique = true)
    private String fcmToken;

    @Column(name = "device_os")
    private String deviceOs;

    @UpdateTimestamp
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    public FcmToken(Long employeeId, String fcmToken, String deviceOs) {
        this.employeeId = employeeId;
        this.fcmToken = fcmToken;
        this.deviceOs = deviceOs;
    }

	public void setEmployeeId(Long employeeId) {
		this.employeeId = employeeId;
	}

	public void setDeviceOs(String deviceOs) {
		this.deviceOs = deviceOs;
	}
}
