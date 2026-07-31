package com.dyinfotech.annualleavebackend.repository.query;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

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
	
	private NumberExpression<Float> adjustedDaysSum(String plusSign) {
		return new CaseBuilder().when(qLeaveAdjustment.sign.eq(plusSign))
				                .then(qLeaveAdjustment.leaveDays)
				                .otherwise(qLeaveAdjustment.leaveDays.negate())
				                .sum();
	}
	
	@Override
	public Float sumAdjustedLeaveDays(Long employeeId, String year, String plusSign) {
	    Float result = queryFactory.select(adjustedDaysSum(plusSign))
						            .from(qLeaveAdjustment)
						            .where(
						            	qLeaveAdjustment.employeeId.eq(employeeId),
						            	qLeaveAdjustment.year.eq(year)
						            )
						            .fetchOne();

	    return result != null ? result : 0.0f;
	}
	
	@Override
	public Map<Long, Float> sumAdjustedLeaveDays(Collection<Long> employeeIds, String year, String plusSign) {
	    NumberExpression<Float> totalAdjustedDays = adjustedDaysSum(plusSign);

	    return queryFactory.select(
				                    qLeaveAdjustment.employeeId,
				                    totalAdjustedDays
				            )
				            .from(qLeaveAdjustment)
				            .where(
				                    qLeaveAdjustment.employeeId.in(employeeIds),
				                    qLeaveAdjustment.year.eq(year)
				            )
				            .groupBy(qLeaveAdjustment.employeeId)
				            .fetch()
				            .stream()
				            .collect(Collectors.toMap(
				                    tuple -> tuple.get(qLeaveAdjustment.employeeId),
				                    tuple -> {
				                        Float value = tuple.get(totalAdjustedDays);
				                        return value == null ? 0.0f : value;
				                    }
				            ));
	}

}
