package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.dyinfotech.annualleavebackend.domain.QRefreshTokenSession;
import com.dyinfotech.annualleavebackend.domain.RefreshTokenSession;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RefreshTokenSessionRepositoryImpl implements RefreshTokenSessionRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private static final QRefreshTokenSession qSession = QRefreshTokenSession.refreshTokenSession;

    @Override
    public Optional<RefreshTokenSession> findByIdForUpdate(String sessionId) {
        return Optional.ofNullable(
                queryFactory.selectFrom(qSession)
                        .where(qSession.sessionId.eq(sessionId))
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .fetchOne());
    }

    @Override
    public long deleteTerminalBefore(LocalDateTime cutoff) {
        return queryFactory.delete(qSession)
                .where(
                        qSession.revokedAt.isNotNull().and(qSession.revokedAt.lt(cutoff))
                                .or(qSession.absoluteExpiresAt.lt(cutoff))
                                .or(qSession.idleExpiresAt.lt(cutoff)))
                .execute();
    }

    @Override
    public long revokeActiveByEmployeeId(
            Long employeeId,
            LocalDateTime revokedAt,
            String reason) {
        return queryFactory.update(qSession)
                .set(qSession.revokedAt, revokedAt)
                .set(qSession.revokedReason, reason)
                .where(
                        qSession.employeeId.eq(employeeId),
                        qSession.revokedAt.isNull())
                .execute();
    }
}
