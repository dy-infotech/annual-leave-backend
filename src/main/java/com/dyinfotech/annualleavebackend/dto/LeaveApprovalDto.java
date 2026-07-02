package com.dyinfotech.annualleavebackend.dto;

import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class LeaveApprovalDto {

    @Getter
    @Builder
    public static class LeaveApprovalResponse {
        private Long requestId;
        private String status;
        private String approverName;
        private LocalDateTime processedAt;

        public static LeaveApprovalResponse from(LeaveRequest leaveRequest) {
            return LeaveApprovalResponse.builder()
                    .requestId(leaveRequest.getRequestId())
                    .status(leaveRequest.getStatus().name())
                    .approverName(leaveRequest.getApprover().getName())
                    .processedAt(leaveRequest.getProcessedAt())
                    .build();
        }
    }
}
