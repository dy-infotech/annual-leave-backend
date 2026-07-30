package com.dyinfotech.annualleavebackend.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DashboardDto {

    private MyLeaveInfoResponse myLeaveInfoResponse;
    private LeaveRequestSummaryResponse myRequestSummary;
    private LeaveRequestSummaryResponse allEmployeeRequestSummary;  // 관리자만 값이 할당되며, 일반 사용자는 null (프론트에서 이 필드의 존재 여부로 관리자 전용 섹션을 보여줄지 말지 판단)

    @Builder
    public DashboardDto(MyLeaveInfoResponse myLeaveInfoResponse, LeaveRequestSummaryResponse myRequestSummary, LeaveRequestSummaryResponse allEmployeeRequestSummary) {
        this.myLeaveInfoResponse = myLeaveInfoResponse;
        this.myRequestSummary = myRequestSummary;
        this.allEmployeeRequestSummary = allEmployeeRequestSummary;
    }

    @Getter
    @Builder
    public static class MyLeaveInfoResponse {
        private Float totalLeaveDays;      // 총 배정
        private Float usedLeaveDays;       // 사용
        private Float remainingLeaveDays;  // 잔여
    }

    @Getter
    @Builder
    public static class LeaveRequestSummaryResponse {
        private long pendingCount;      // 승인 대기
        private long approvedCount;     // 승인 완료
        private long rejectedCount;     // 반려
    }
}
