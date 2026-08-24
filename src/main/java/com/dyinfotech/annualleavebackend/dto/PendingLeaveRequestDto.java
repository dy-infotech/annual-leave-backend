package com.dyinfotech.annualleavebackend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.dyinfotech.annualleavebackend.domain.LeaveRequest;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PendingLeaveRequestDto {

    @Getter
    @Builder
    public static class PendingLeaveRequestResponse {
        private Long requestId;
        private String employeeNumber;
        private String employeeName;
        private String department;
        private String team;
        private String position;
        private LocalDate startDate;
        private LocalDate endDate;
        private Float useDays;
        private LocalDateTime createdAt;
        private String leaveType;

        public static PendingLeaveRequestResponse from(LeaveRequest leaveRequest) {
            return PendingLeaveRequestResponse.builder()
                    .requestId(leaveRequest.getRequestId())
                    .employeeNumber(leaveRequest.getEmployee().getEmployeeNumber())
                    .employeeName(leaveRequest.getEmployee().getName())
                    .department(leaveRequest.getEmployee().getDepartment())
                    .team(leaveRequest.getEmployee().getTeam().getTeamName())
                    .position(leaveRequest.getEmployee().getPosition())
                    .startDate(leaveRequest.getStartDate())
                    .endDate(leaveRequest.getEndDate())
                    .useDays(leaveRequest.getUseDays())
                    .createdAt(leaveRequest.getCreatedAudit().getCreatedAt())
                    .leaveType(leaveRequest.getLeaveType())
                    .build();
        }
    }
}
