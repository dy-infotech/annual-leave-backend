package com.dyinfotech.annualleavebackend.dto;

import java.time.LocalDateTime;

import com.dyinfotech.annualleavebackend.domain.LeaveRequest;

import jakarta.validation.constraints.Size;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LeaveRejectDto {

    @Getter
    @NoArgsConstructor
    public static class LeaveRejectRequest {

        @Size(max = 200, message = "반려 사유는 200자 이하여야 합니다.")
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
