package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.domain.FcmToken;
import com.dyinfotech.annualleavebackend.repository.query.FcmTokenRepositoryCustom;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long>, FcmTokenRepositoryCustom {
	// 토큰 존재 여부 확인용 (UPSERT 구현체에서 사용)
	Optional<FcmToken> findByToken(String token);
	List<FcmToken> findAllByUpdatedAuditUpdatedAtBefore(LocalDateTime threshold);
	
	// 로그아웃 시 토큰 삭제
	@Transactional
	void deleteByToken(String token);
	

}
