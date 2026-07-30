package com.dyinfotech.annualleavebackend.repository.projection;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;

public interface LeaveRequestStatusCount {
    LeaveRequestStatus getStatus();
    Long getCount();
}
