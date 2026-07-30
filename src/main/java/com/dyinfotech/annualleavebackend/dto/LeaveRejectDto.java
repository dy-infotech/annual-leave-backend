package com.dyinfotech.annualleavebackend.dto;

import java.time.LocalDateTime;

import com.dyinfotech.annualleavebackend.domain.LeaveRequest;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class LeaveRejectDto {

    @Getter
    @NoArgsConstructor
    public static class LeaveRejectRequest {

        private String rejectReason;    // 사유 없이 반려 가능(선택 입력)
    }

    @Getter
    @Builder
    public static class LeaveRejectResponse {
        private Long requestId;
        private String status;
        private String managerName;
        private String rejectReason;
        private LocalDateTime managedAt;

        public static LeaveRejectResponse from(LeaveRequest leaveRequest) {
            return LeaveRejectResponse.builder()
                    .requestId(leaveRequest.getRequestId())
                    .status(leaveRequest.getStatus().name())
                    .managerName(leaveRequest.getManager().getName())
                    .rejectReason(leaveRequest.getRejectReason())
                    .managedAt(leaveRequest.getManagedAt())
                    .build();
        }
    }
}
