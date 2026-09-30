package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.domain.PasswordResetToken;

import jakarta.persistence.LockModeType;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
              from PasswordResetToken token
             where token.tokenHash = :tokenHash
               and token.consumedAt is null
            """)
    Optional<PasswordResetToken> findUnusedForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            delete from PasswordResetToken token
             where token.employeeId = :employeeId
               and token.consumedAt is null
            """)
    int deleteUnusedByEmployeeId(@Param("employeeId") Long employeeId);

    @Modifying
    @Query("delete from PasswordResetToken token where token.expiresAt < :threshold")
    int deleteExpired(@Param("threshold") LocalDateTime threshold);
}
