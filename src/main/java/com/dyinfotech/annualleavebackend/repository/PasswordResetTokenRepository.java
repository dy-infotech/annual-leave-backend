package com.dyinfotech.annualleavebackend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.PasswordResetToken;
import com.dyinfotech.annualleavebackend.repository.query.PasswordResetTokenRepositoryCustom;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long>, PasswordResetTokenRepositoryCustom {

    Optional<PasswordResetToken> findByTokenHashAndConsumedAtIsNull(String tokenHash);
}
