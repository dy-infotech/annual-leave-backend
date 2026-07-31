package com.dyinfotech.annualleavebackend.repository.projection;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;

public record LeaveRequestStatusCount(LeaveRequestStatus status, Long count) {
	
}
