package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDateTime;
import java.util.Optional;

import com.dyinfotech.annualleavebackend.domain.PasswordResetToken;

public interface PasswordResetTokenRepositoryCustom {

    Optional<PasswordResetToken> findUnusedForUpdate(String tokenHash);

    int deleteUnusedByEmployeeId(Long employeeId);

    int deleteExpired(LocalDateTime threshold);
}
