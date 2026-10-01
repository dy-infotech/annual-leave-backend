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
	// 오래 사용하지 않은 토큰을 식별자 순서로 나눠 조회한다
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
		
		// 변경 쿼리 후 기존 영속성 상태를 비운다
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
	// 기존 소유자와 세션 연결이 같을 때만 토큰 정보를 갱신한다
	public int updateTokenAndTouchIfBinding(
			Long expectedEmployeeId,
			String expectedAuthSessionMarker,
			Long employeeId,
			String authSessionMarker,
			String deviceOs,
			LocalDateTime now,
			String token) {
		BooleanExpression expectedMarker = expectedAuthSessionMarker == null
				? qFcmToken.authSessionMarker.isNull()
				: qFcmToken.authSessionMarker.eq(expectedAuthSessionMarker);

		int result = (int) queryFactory.update(qFcmToken)
				.set(qFcmToken.employeeId, employeeId)
				.set(qFcmToken.authSessionMarker, authSessionMarker)
				.set(qFcmToken.deviceOs, deviceOs)
				.set(qFcmToken.updatedAudit.updatedAt, now)
				.set(qFcmToken.updatedAudit.updatedIp, IpContext.get())
				.where(
						qFcmToken.token.eq(token),
						qFcmToken.employeeId.eq(expectedEmployeeId),
						expectedMarker)
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
	// 현재 소유자와 세션 연결이 일치하는 토큰만 삭제한다
	public long deleteByTokenAndBinding(
			String token,
			Long employeeId,
			String authSessionMarker) {
		BooleanExpression markerCondition = authSessionMarker == null
				? qFcmToken.authSessionMarker.isNull()
				: qFcmToken.authSessionMarker.eq(authSessionMarker);

		long deleted = queryFactory.delete(qFcmToken)
				.where(
						qFcmToken.token.eq(token),
						qFcmToken.employeeId.eq(employeeId),
						markerCondition)
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

		// 변경 쿼리 후 기존 영속성 상태를 비운다
        entityManager.clear();
	}

}
