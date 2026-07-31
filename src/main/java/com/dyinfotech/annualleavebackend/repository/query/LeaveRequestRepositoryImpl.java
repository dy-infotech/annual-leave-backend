package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.domain.QLeaveRequest;
import com.dyinfotech.annualleavebackend.repository.projection.LeaveRequestStatusCount;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class LeaveRequestRepositoryImpl implements LeaveRequestRepositoryCustom {

    private final JPAQueryFactory queryFactory;
	private final EntityManager entityManager;

    private static final QLeaveRequest qLeaveRequest = QLeaveRequest.leaveRequest;

	@Override
	public Float sumApprovedUseDays(Long employeeId, List<LeaveRequestStatus> status, LocalDate startRange,
			LocalDate endRange) {
		Float result = queryFactory.select(qLeaveRequest.useDays.sum())
					                .from(qLeaveRequest)
					                .where(
					                    qLeaveRequest.employee.employeeId.eq(employeeId),
					                    qLeaveRequest.status.in(status),
					                    qLeaveRequest.startDate.between(startRange, endRange)
					                )
					                .fetchOne();

        return result != null ? result : 0.0f;
	}

	@Override
	public List<LeaveRequestStatusCount> countByStatus(Long employeeId, LocalDate endRange, LocalDate startRange) {
		return queryFactory.select(
			                    Projections.constructor(
			                        LeaveRequestStatusCount.class,
			                        qLeaveRequest.status,
			                        qLeaveRequest.count()
			                    )
			                )
			                .from(qLeaveRequest)
			                .where(
			                    qLeaveRequest.employee.employeeId.eq(employeeId),
			                    qLeaveRequest.startDate.loe(endRange),
			                    qLeaveRequest.endDate.goe(startRange)
			                )
			                .groupBy(qLeaveRequest.status)
			                .fetch();
	}

	@Override
	public List<LeaveRequestStatusCount> countByStatus(Collection<String> directTeams, Collection<String> accessibleTeams, LocalDate endRange, LocalDate startRange) {
		BooleanExpression pendingCondition =
		        qLeaveRequest.status.eq(LeaveRequestStatus.PENDING)
		            .and(qLeaveRequest.employee.team.in(directTeams));

		BooleanExpression processedCondition =
		        qLeaveRequest.status.in(
		                LeaveRequestStatus.APPROVED,
		                LeaveRequestStatus.REJECTED
		        )
		        .and(qLeaveRequest.employee.team.in(accessibleTeams));

		BooleanExpression teamCondition = pendingCondition.or(processedCondition);

	    return queryFactory.select(
				                Projections.constructor(
				                    LeaveRequestStatusCount.class,
				                    qLeaveRequest.status,
				                    qLeaveRequest.count()
				                )
				            )
				            .from(qLeaveRequest)
				            .where(
				                teamCondition,
				                qLeaveRequest.startDate.loe(endRange),
				                qLeaveRequest.endDate.goe(startRange)
				            )
				            .groupBy(qLeaveRequest.status)
				            .fetch();
	}

	@Override
	@Transactional
	public int updateLeaveRequest(Long requestId, Employee approver, String rejectReason,
			LeaveRequestStatus sourceStatus, LeaveRequestStatus targetStatus, LocalDateTime now) {
		int result = (int) queryFactory.update(qLeaveRequest)
						                .set(qLeaveRequest.status, targetStatus)
						                .set(qLeaveRequest.manager, approver)
						                .set(qLeaveRequest.managedAt, now)
						                .set(qLeaveRequest.rejectReason, rejectReason)
						                .where(
						                    qLeaveRequest.requestId.eq(requestId),
						                    qLeaveRequest.status.eq(sourceStatus)
						                )
						                .execute();

		// 쿼리 실행 후 영속성 컨텍스트 자동 클리어
	    entityManager.clear();
	    
	    return result;
	}

	@Override
	public List<LeaveRequest> searchLeaveRequests(Long employeeId, LocalDate startDate, LocalDate endDate,
			LeaveRequestStatus status) {
		BooleanBuilder builder = new BooleanBuilder();

        if (employeeId != null) {
            builder.and(
                qLeaveRequest.employee.employeeId.eq(employeeId)
            );
        }

        if (startDate != null) {
            builder.and(
                qLeaveRequest.startDate.goe(startDate)
            );
        }

        if (endDate != null) {
            builder.and(
                qLeaveRequest.endDate.loe(endDate)
            );
        }

        if (status != null) {
            builder.and(
                qLeaveRequest.status.eq(status)
            );
        }

        return queryFactory
                .selectFrom(qLeaveRequest)
                .where(builder)
                .orderBy(qLeaveRequest.createdAt.desc())
                .fetch();
	}
	
	@Override
	public List<LeaveRequest> searchLeaveRequestsByTeam(
	        LocalDate startDate,
	        LocalDate endDate,
	        LeaveRequestStatus status,
	        List<String> teams) {

	    BooleanBuilder builder = new BooleanBuilder();

	    if (startDate != null) {
	        builder.and(qLeaveRequest.startDate.goe(startDate));
	    }

	    if (endDate != null) {
	        builder.and(qLeaveRequest.endDate.loe(endDate));
	    }

	    if (status != null) {
	        builder.and(qLeaveRequest.status.eq(status));
	    }

	    if (teams != null && !teams.isEmpty()) {
	        builder.and(qLeaveRequest.employee.team.in(teams));
	    }

	    return queryFactory
	            .selectFrom(qLeaveRequest)
	            .where(builder)
	            .orderBy(qLeaveRequest.createdAt.desc())
	            .fetch();
	}

}
