package com.dyinfotech.annualleavebackend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LeaveRequestListDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LeaveRequestListRequest {

        private Long employeeId;       // employee PK (null일 경우, 전직원)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;   // 조회 시작일 (null일 경우 전체 기간)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
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
        private String employeeNumber;
        private String position;
        private String department;
        private LocalDateTime requestedAt;   // 휴가 신청일
        private String leaveType;		// 휴가 종류 ({@link LeaveType})
        private LocalDate startDate;    // 휴가 기간 시작
        private LocalDate endDate;      // 휴가 기간 종료
        private Float useDays;
        private String status;
        private String rejectReason;   // 반려 시에만 값 할당, 그 외엔 null

        public static LeaveRequestListResponse from(LeaveRequest leaveRequest) {
            return LeaveRequestListResponse.builder()
                    .requestId(leaveRequest.getRequestId())
                    .employeeName(leaveRequest.getEmployee().getName())
                    .employeeNumber(leaveRequest.getEmployee().getEmployeeNumber())
                    .position(leaveRequest.getEmployee().getPosition())
                    .department(leaveRequest.getEmployee().getDepartment())
                    .requestedAt(leaveRequest.getCreatedAudit().getCreatedAt())
                    .leaveType(leaveRequest.getLeaveType())
                    .startDate(leaveRequest.getStartDate())
                    .endDate(leaveRequest.getEndDate())
                    .useDays(leaveRequest.getUseDays())
                    .status(leaveRequest.getStatus().name())
                    .rejectReason(leaveRequest.getRejectReason())
                    .build();
        }
    }
}
