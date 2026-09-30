package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "auth_refresh_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenSession {
    @Id
    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @Column(name = "previous_valid_until")
    private LocalDateTime previousValidUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_rotated_at", nullable = false)
    private LocalDateTime lastRotatedAt;

    @Column(name = "idle_expires_at", nullable = false)
    private LocalDateTime idleExpiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private LocalDateTime absoluteExpiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_reason", length = 40)
    private String revokedReason;

    @Column(name = "rotation_count", nullable = false)
    private int rotationCount;

    public RefreshTokenSession(
            String sessionId,
            Long employeeId,
            String tokenHash,
            LocalDateTime now,
            LocalDateTime idleExpiresAt,
            LocalDateTime absoluteExpiresAt) {
        this.sessionId = sessionId;
        this.employeeId = employeeId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.lastRotatedAt = now;
        this.idleExpiresAt = idleExpiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(idleExpiresAt) || !now.isBefore(absoluteExpiresAt);
    }

    public void rotate(
            String nextHash,
            LocalDateTime now,
            LocalDateTime nextIdle,
            LocalDateTime previousGraceUntil) {
        previousTokenHash = tokenHash;
        previousValidUntil = previousGraceUntil;
        tokenHash = nextHash;
        lastRotatedAt = now;
        idleExpiresAt = nextIdle;
        rotationCount++;
    }

    public void revoke(LocalDateTime now, String reason) {
        if (revokedAt == null) {
            revokedAt = now;
            revokedReason = reason;
        }
    }
}
