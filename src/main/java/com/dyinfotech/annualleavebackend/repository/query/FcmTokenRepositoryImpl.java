package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.common.IpContext;
import com.dyinfotech.annualleavebackend.domain.QFcmToken;
import com.querydsl.core.types.dsl.BooleanExpression;
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
	public List<com.dyinfotech.annualleavebackend.domain.FcmToken> findInactiveTokensBatch(
			LocalDateTime threshold,
			Long afterTokenId,
			int limit) {
		BooleanExpression afterId = afterTokenId == null
				? null
				: qFcmToken.tokenId.gt(afterTokenId);
		return queryFactory.selectFrom(qFcmToken)
				.where(
						qFcmToken.updatedAudit.updatedAt.before(threshold),
						afterId)
				.orderBy(qFcmToken.tokenId.asc())
				.limit(limit)
				.fetch();
	}

	@Override
	@Transactional
	public int updateTokenAndTouch(Long employeeId, String deviceOs, LocalDateTime now, String token) {
		int result = (int) queryFactory.update(qFcmToken)
						                .set(qFcmToken.employeeId, employeeId)
						                .set(qFcmToken.deviceOs, deviceOs)
						                .set(qFcmToken.updatedAudit.updatedAt, now)
						                .set(qFcmToken.updatedAudit.updatedIp, IpContext.get())
						                .where(qFcmToken.token.eq(token))
						                .execute();
		
		// 쿼리 실행 후 영속성 컨텍스트 자동 클리어
        entityManager.clear();

        return result;
	}
	
	@Override
	@Transactional
	public int updateTokenAndTouchIfOwner(
			Long expectedEmployeeId,
			Long employeeId,
			String deviceOs,
			LocalDateTime now,
			String token) {
		int result = (int) queryFactory.update(qFcmToken)
				.set(qFcmToken.employeeId, employeeId)
				.set(qFcmToken.deviceOs, deviceOs)
				.set(qFcmToken.updatedAudit.updatedAt, now)
				.set(qFcmToken.updatedAudit.updatedIp, IpContext.get())
				.where(
						qFcmToken.token.eq(token),
						qFcmToken.employeeId.eq(expectedEmployeeId))
				.execute();
		entityManager.clear();
		return result;
	}

	@Override
	@Transactional
	public long deleteByTokenAndEmployeeId(String token, Long employeeId) {
	    long deleted = queryFactory.delete(qFcmToken)
	            .where(
	                    qFcmToken.token.eq(token),
	                    qFcmToken.employeeId.eq(employeeId))
	            .execute();
	    entityManager.clear();
	    return deleted;
	}

	@Override
	@Transactional
	public void deleteByUpdatedAtBefore(LocalDateTime threshold) {
	    queryFactory.delete(qFcmToken)
	    			.where(qFcmToken.updatedAudit.updatedAt.before(threshold))
			        .execute();

		// 쿼리 실행 후 영속성 컨텍스트 자동 클리어
        entityManager.clear();
	}

}
