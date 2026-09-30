package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDateTime;
import java.util.Optional;

import com.dyinfotech.annualleavebackend.domain.RefreshTokenSession;

public interface RefreshTokenSessionRepositoryCustom {
    Optional<RefreshTokenSession> findByIdForUpdate(String sessionId);
    long deleteTerminalBefore(LocalDateTime cutoff);
    long revokeActiveByEmployeeId(Long employeeId, LocalDateTime revokedAt, String reason);
}
