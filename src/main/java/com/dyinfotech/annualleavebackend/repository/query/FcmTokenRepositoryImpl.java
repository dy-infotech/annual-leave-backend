package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDateTime;

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

		// 쿼리 실행 후 영속성 컨텍스트 자동 클리어
        entityManager.clear();
	}

}
