package com.dyinfotech.annualleavebackend.repository.query;

import java.util.Collection;
import java.util.Map;

public interface LeaveAdjustmentRepositoryCustom {
	Float sumAdjustedLeaveDays(Long employeeId, String year, String plusSign);
	Map<Long, Float> sumAdjustedLeaveDays(Collection<Long> employeeIds, String year, String plusSign);
}
