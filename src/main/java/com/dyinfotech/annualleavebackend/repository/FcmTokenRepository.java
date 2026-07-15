package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.domain.FcmToken;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {
	// 토큰 존재 여부 확인용 (UPSERT 구현체에서 사용)
	Optional<FcmToken> findByFcmToken(String fcmToken);
	
	// 로그아웃 시 토큰 삭제
	void deleteByFcmToken(String fcmToken);
	
	// 더티 체킹 우회하고 update_at을 현재 시간으로 갱신
	@Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트 자동 클리어
    @Query("UPDATE FcmToken f " +
           "SET f.employeeId = :employeeId, f.deviceOs = :deviceOs, f.updatedAt = :now " +
           "WHERE f.fcmToken = :fcmToken")
    int updateTokenAndTouch(@Param("employeeId") Long employeeId, 
                            @Param("deviceOs") String deviceOs, 
                            @Param("now") LocalDateTime now, 
                            @Param("fcmToken") String fcmToken);
	
	@Modifying
	void deleteByUpdatedAtBefore(LocalDateTime threshold);
}
