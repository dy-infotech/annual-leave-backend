package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.common.IpContext;
import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.domain.QLeaveRequest;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.repository.projection.LeaveRequestStatusCount;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
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
	public float sumApprovedUseDays(Long employeeId, List<LeaveRequestStatus> status, LocalDate startRange,
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
	public Map<Long, Float> sumApprovedUseDays(Collection<Long> employeeIds, List<LeaveRequestStatus> status,
	                                           LocalDate startRange, LocalDate endRange) {
	    NumberExpression<Float> sumUseDays = qLeaveRequest.useDays.sum();

	    Map<Long, Float> result = queryFactory.select(
									                qLeaveRequest.employee.employeeId,
									                sumUseDays
									            )
									            .from(qLeaveRequest)
									            .where(
									                qLeaveRequest.employee.employeeId.in(employeeIds),
									                qLeaveRequest.status.in(status),
									                qLeaveRequest.startDate.between(startRange, endRange)
									            )
									            .groupBy(qLeaveRequest.employee.employeeId)
									            .fetch()
									            .stream()
									            .collect(Collectors.toMap(
									                tuple -> tuple.get(qLeaveRequest.employee.employeeId),
									                tuple -> {
									                    Float value = tuple.get(sumUseDays);
									                    return value != null ? value : 0.0f;
									                }
									            ));

	    employeeIds.forEach(employeeId -> result.putIfAbsent(employeeId, 0.0f));

	    return result;
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
	public List<LeaveRequestStatusCount> countByStatus(Long excludeId, Collection<String> directTeams, Collection<Team> accessibleTeams, LocalDate endRange, LocalDate startRange) {
		Set<Long> childTeamProjectManagerIds = new HashSet<>();
		Set<String> accessibleTeamNames = new HashSet<>();
		for (Team team : accessibleTeams) {
		    accessibleTeamNames.add(team.getTeam());
		    if (directTeams.contains(team.getParentTeam()) && team.getProjectManager() != null) {
		        childTeamProjectManagerIds.add(team.getProjectManager().getEmployeeId());
		    }
		}
		
		BooleanExpression directTeamCondition = qLeaveRequest.employee.team.in(directTeams);
		if (excludeId != null) {
			directTeamCondition = directTeamCondition.and(qLeaveRequest.employee.employeeId.ne(excludeId));
		}
		BooleanExpression pendingTargetCondition = directTeamCondition;
		if (!childTeamProjectManagerIds.isEmpty()) {
		    pendingTargetCondition = pendingTargetCondition.or(qLeaveRequest.employee.employeeId.in(childTeamProjectManagerIds));
		}
		BooleanExpression pendingCondition = qLeaveRequest.status.eq(LeaveRequestStatus.PENDING)
													            .and(pendingTargetCondition);

		BooleanExpression processedCondition = qLeaveRequest.status.in(LeaveRequestStatus.APPROVED, LeaveRequestStatus.REJECTED)
		        													.and(qLeaveRequest.employee.team.in(accessibleTeamNames));
		
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
	public List<LeaveRequest> findByStatusAndTeamsInRange(Long excludeId, LeaveRequestStatus status, Collection<String> directTeams, Collection<Long> childTeamProjectManagerIds, LocalDate endRange, LocalDate startRange) {
		BooleanExpression targetCondition = qLeaveRequest.employee.team.in(directTeams);
		if (excludeId != null) {
		    targetCondition = targetCondition.and(qLeaveRequest.employee.employeeId.ne(excludeId));
		}
		if (!childTeamProjectManagerIds.isEmpty()) {
		    targetCondition = targetCondition.or(qLeaveRequest.employee.employeeId.in(childTeamProjectManagerIds));
		}
		
		return queryFactory.selectFrom(qLeaveRequest)
	                        .where(
	                            qLeaveRequest.status.eq(status),
	                            targetCondition,
	                            qLeaveRequest.startDate.loe(endRange),
	                            qLeaveRequest.endDate.goe(startRange)
	                        )
	                        .orderBy(qLeaveRequest.createdAudit.createdAt.asc())
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
						                .set(qLeaveRequest.managedIp, IpContext.get())
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
	public List<LeaveRequest> searchLeaveRequests(Long employeeId, LocalDate startDate, LocalDate endDate, LeaveRequestStatus status, Collection<String> teams) {
		BooleanBuilder builder = new BooleanBuilder();

        if (employeeId != null) {
            builder.and(qLeaveRequest.employee.employeeId.eq(employeeId));
        }

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
	    
        return queryFactory.selectFrom(qLeaveRequest)
        					.join(qLeaveRequest.employee).fetchJoin()
			                .where(builder)
			                .orderBy(qLeaveRequest.createdAudit.createdAt.desc())
			                .fetch();
	}

}
