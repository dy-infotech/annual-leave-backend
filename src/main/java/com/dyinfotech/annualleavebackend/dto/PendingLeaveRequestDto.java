package com.dyinfotech.annualleavebackend.dto;

import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PendingLeaveRequestDto {

    @Getter
    @Builder
    public static class PendingLeaveRequestResponse {
        private Long requestId;
        private String employeeNo;
        private String employeeName;
        private String department;
        private String position;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal useDays;
        private LocalDateTime createdAt;

        public static PendingLeaveRequestResponse from(LeaveRequest leaveRequest) {
            return PendingLeaveRequestResponse.builder()
                    .requestId(leaveRequest.getRequestId())
                    .employeeNo(leaveRequest.getEmployee().getEmployeeNo())
                    .employeeName(leaveRequest.getEmployee().getName())
                    .department(leaveRequest.getEmployee().getDepartment())
                    .position(leaveRequest.getEmployee().getPosition())
                    .startDate(leaveRequest.getStartDate())
                    .endDate(leaveRequest.getEndDate())
                    .useDays(leaveRequest.getUseDays())
                    .createdAt(leaveRequest.getCreatedAt())
                    .build();
        }
    }
}
