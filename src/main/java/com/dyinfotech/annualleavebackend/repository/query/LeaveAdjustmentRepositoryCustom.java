package com.dyinfotech.annualleavebackend.repository.query;

public interface LeaveAdjustmentRepositoryCustom {
	Float sumAdjustedLeaveDays(Long employeeId, String year, String plusSign);
}
