package com.dyinfotech.annualleavebackend.dto;

import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.domain.LeaveRequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class LeaveRequestListDto {

    @Getter
    public static class LeaveRequestListRequest {

        private Long employeeId;       // employee PK (null일 경우, 전직원)
        private LocalDate startDate;   // 조회 시작일 (null일 경우 전체 기간)
        private LocalDate endDate;     // 조회 종료일 (null일 경우 전체 기간)
        private LeaveRequestStatus status;    // null일 경우 전체 상태

        public LeaveRequestListRequest(Long employeeId,  LocalDate startDate, LocalDate endDate, LeaveRequestStatus status) {
            this.employeeId = employeeId;
            this.startDate = startDate;
            this.endDate = endDate;
            this.status = status;
        }
    }

    @Getter
    @Builder
    public static class LeaveRequestListResponse {
        private Long requestId;
        private String employeeName;
        private String position;
        private String department;
        private LocalDateTime requestedAt;   // 휴가 신청일
        private LocalDate startDate;    // 휴가 기간 시작
        private LocalDate endDate;      // 휴가 기간 종료
        private BigDecimal useDays;
        private String status;

        public static LeaveRequestListResponse from(LeaveRequest leaveRequest) {
            return LeaveRequestListResponse.builder()
                    .requestId(leaveRequest.getRequestId())
                    .employeeName(leaveRequest.getEmployee().getName())
                    .position(leaveRequest.getEmployee().getPosition())
                    .department(leaveRequest.getEmployee().getDepartment())
                    .requestedAt(leaveRequest.getCreatedAt())
                    .startDate(leaveRequest.getStartDate())
                    .endDate(leaveRequest.getEndDate())
                    .useDays(leaveRequest.getUseDays())
                    .status(leaveRequest.getStatus().name())
                    .build();
        }
    }
}
