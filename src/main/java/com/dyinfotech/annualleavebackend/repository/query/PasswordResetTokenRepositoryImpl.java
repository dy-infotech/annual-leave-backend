package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.dyinfotech.annualleavebackend.domain.PasswordResetToken;
import com.dyinfotech.annualleavebackend.domain.QPasswordResetToken;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PasswordResetTokenRepositoryImpl
        implements PasswordResetTokenRepositoryCustom {

    private static final QPasswordResetToken qToken =
            QPasswordResetToken.passwordResetToken;

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<PasswordResetToken> findUnusedForUpdate(String tokenHash) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(qToken)
                        .where(
                                qToken.tokenHash.eq(tokenHash),
                                qToken.consumedAt.isNull())
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .fetchOne());
    }

    @Override
    public int deleteUnusedByEmployeeId(Long employeeId) {
        return Math.toIntExact(
                queryFactory
                        .delete(qToken)
                        .where(
                                qToken.employeeId.eq(employeeId),
                                qToken.consumedAt.isNull())
                        .execute());
    }

    @Override
    public int deleteExpired(LocalDateTime threshold) {
        return Math.toIntExact(
                queryFactory
                        .delete(qToken)
                        .where(qToken.expiresAt.lt(threshold))
                        .execute());
    }
}
