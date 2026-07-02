package com.dyinfotech.annualleavebackend.dto;

import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class LeaveRequestDto {

    @Getter
    @NoArgsConstructor
    public static class LeaveRequestCreateRequest {

        @NotNull(message = "시작일을 입력해주세요.")
        private LocalDate startDate;

        @NotNull(message = "종료일을 입력해주세요.")
        private LocalDate endDate;

        @NotNull(message = "사용일수를 입력해주세요.")
        private BigDecimal useDays;
    }

    @Getter
    @Builder
    public static class LeaveRequestCreateResponse {
        private Long requestId;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal useDays;
        private String status;
        private LocalDateTime createdAt;

        public static LeaveRequestCreateResponse from(LeaveRequest leaveRequest) {
            return LeaveRequestCreateResponse.builder()
                    .requestId(leaveRequest.getRequestId())
                    .startDate(leaveRequest.getStartDate())
                    .endDate(leaveRequest.getEndDate())
                    .useDays(leaveRequest.getUseDays())
                    .status(leaveRequest.getStatus().name())
                    .createdAt(leaveRequest.getCreatedAt())
                    .build();
        }
    }
}
