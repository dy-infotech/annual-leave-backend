package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDateTime;

import org.springframework.stereotype.Repository;

import com.dyinfotech.annualleavebackend.domain.QFcmToken;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FcmTokenRepositoryImpl implements FcmTokenRepositoryCustom {
	private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
	private static final QFcmToken qFcmToken = QFcmToken.fcmToken;
	
	@Override
	public int updateTokenAndTouch(Long employeeId, String deviceOs, LocalDateTime now, String token) {
		int result = (int) queryFactory.update(qFcmToken)
						                .set(qFcmToken.employeeId, employeeId)
						                .set(qFcmToken.deviceOs, deviceOs)
						                .set(qFcmToken.updatedAt, now)
						                .where(qFcmToken.token.eq(token))
						                .execute();
		
		// 쿼리 실행 후 영속성 컨텍스트 자동 클리어
        entityManager.clear();

        return result;
	}

}
