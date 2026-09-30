package com.dyinfotech.annualleavebackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.RefreshTokenSession;
import com.dyinfotech.annualleavebackend.repository.query.RefreshTokenSessionRepositoryCustom;

public interface RefreshTokenSessionRepository
        extends JpaRepository<RefreshTokenSession, String>, RefreshTokenSessionRepositoryCustom {
}
