package com.dyinfotech.annualleavebackend.domain;

public enum LeaveRequestStatus {
    PENDING,    // 승인 대기
    APPROVED,   // 승인 완료
    REJECTED    // 반려
}
