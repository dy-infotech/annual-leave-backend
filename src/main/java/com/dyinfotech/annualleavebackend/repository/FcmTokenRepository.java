package com.dyinfotech.annualleavebackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.FcmToken;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {
	// 토큰 존재 여부 확인용 (UPSERT 구현체에서 사용)
	Optional<FcmToken> findByFcmToken(String fcmToken);
	
	// 특정 유저의 모든 기기 토큰 조회 (인사 이동 시 사용)
	List<FcmToken> findByEmployeeId(Long employeeId);
	
	// 로그아웃 시 토큰 삭제
	void deleteByFcmToken(String fcmToken);
}
