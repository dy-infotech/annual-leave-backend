package com.dyinfotech.annualleavebackend.repository.query;

import org.springframework.stereotype.Repository;

import com.dyinfotech.annualleavebackend.domain.QLeaveAdjustment;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class LeaveAdjustmentRepositoryImpl implements LeaveAdjustmentRepositoryCustom {
	private final JPAQueryFactory queryFactory;
	private static final QLeaveAdjustment qLeaveAdjustment = QLeaveAdjustment.leaveAdjustment;

	@Override
	public Float sumAdjustedLeaveDays(Long employeeId, String year, String plusSign) {
		NumberExpression<Float> adjustedDays = new CaseBuilder().when(qLeaveAdjustment.sign.eq(plusSign))
												                .then(qLeaveAdjustment.leaveDays)
												                .otherwise(qLeaveAdjustment.leaveDays.negate());

	    Float result = queryFactory.select(adjustedDays.sum())
						            .from(qLeaveAdjustment)
						            .where(
						            	qLeaveAdjustment.employeeId.eq(employeeId),
						            	qLeaveAdjustment.year.eq(year)
						            )
						            .fetchOne();

	    return result != null ? result : 0.0f;
	}

}
