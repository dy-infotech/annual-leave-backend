package com.dyinfotech.annualleavebackend.dto;

import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class LeaveRejectDto {

    @Getter
    @NoArgsConstructor
    public static class LeaveRejectRequest {

        @NotBlank(message = "반려 사유를 입력해주세요.")
        private String rejectReason;
    }

    @Getter
    @Builder
    public static class LeaveRejectResponse {
        private Long requestId;
        private String status;
        private String approverName;
        private String rejectReason;
        private LocalDateTime processedAt;

        public static LeaveRejectResponse from(LeaveRequest leaveRequest) {
            return LeaveRejectResponse.builder()
                    .requestId(leaveRequest.getRequestId())
                    .status(leaveRequest.getStatus().name())
                    .approverName(leaveRequest.getApprover().getName())
                    .rejectReason(leaveRequest.getRejectReason())
                    .processedAt(leaveRequest.getProcessedAt())
                    .build();
        }
    }
}
