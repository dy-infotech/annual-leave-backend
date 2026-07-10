package com.dyinfotech.annualleavebackend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.dyinfotech.annualleavebackend.domain.LeaveRequest;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class LeaveRequestDto {

    @Getter
    @NoArgsConstructor
    public static class LeaveRequestCreateRequest {
    	@NotNull(message = "휴가유형을 입력해주세요.")
    	private String leaveType;

        @NotNull(message = "시작일을 입력해주세요.")
        private LocalDate startDate;

        @NotNull(message = "종료일을 입력해주세요.")
        private LocalDate endDate;

        @NotNull(message = "사용일수를 입력해주세요.")
        private Float useDays;
    }

    @Getter
    @Builder
    public static class LeaveRequestCreateResponse {
        private Long requestId;
        private String leaveType;
        private LocalDate startDate;
        private LocalDate endDate;
        private Float useDays;
        private String status;
        private LocalDateTime createdAt;

        public static LeaveRequestCreateResponse from(LeaveRequest leaveRequest) {
            return LeaveRequestCreateResponse.builder()
                    .requestId(leaveRequest.getRequestId())
                    .leaveType(leaveRequest.getLeaveType())
                    .startDate(leaveRequest.getStartDate())
                    .endDate(leaveRequest.getEndDate())
                    .useDays(leaveRequest.getUseDays())
                    .status(leaveRequest.getStatus().name())
                    .createdAt(leaveRequest.getCreatedAt())
                    .build();
        }
    }
}
