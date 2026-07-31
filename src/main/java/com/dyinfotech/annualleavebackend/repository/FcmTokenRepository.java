package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.dyinfotech.annualleavebackend.domain.FcmToken;
import com.dyinfotech.annualleavebackend.repository.query.FcmTokenRepositoryCustom;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long>, FcmTokenRepositoryCustom {
	// 토큰 존재 여부 확인용 (UPSERT 구현체에서 사용)
	Optional<FcmToken> findByToken(String token);
	
	// 로그아웃 시 토큰 삭제
	void deleteByToken(String token);
	
//	// 더티 체킹 우회하고 update_at을 현재 시간으로 갱신
//	@Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트 자동 클리어
//    @Query("UPDATE FcmToken f " +
//           "SET f.employeeId = :employeeId, f.deviceOs = :deviceOs, f.updatedAt = :now " +
//           "WHERE f.token = :token")
//    int updateTokenAndTouch(@Param("employeeId") Long employeeId, 
//                            @Param("deviceOs") String deviceOs, 
//                            @Param("now") LocalDateTime now, 
//                            @Param("token") String token);
	
	@Modifying
	void deleteByUpdatedAtBefore(LocalDateTime threshold);
}
