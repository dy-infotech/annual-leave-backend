package com.dyinfotech.annualleavebackend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;

import lombok.Builder;
import lombok.Getter;

public class LeaveRequestDetailDto {

    @Getter
    @Builder
    public static class LeaveRequestDetailResponse {
        // 휴가자 정보
        private final String employeeNumber;
        private final String employeeName;
        private final String position;
        private final String department;
        private final String team;

        // 휴가 정보
        private final String leaveType;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final Float useDays;
        private final Float prevTotalLeaveDays;
        private final Float currTotalLeaveDays;
        private final LeaveRequestStatus status;
        private final String leaveReason; // 권한 없으면 null
        private final String rejectReason; // 반려 시에만 값 존재
        private final LocalDateTime managedAt; // 결재 처리일

        // 결재자 정보 (미배정 시 null)
        private final String approverNumber;
        private final String approverName;
        private final String approverPosition;
        private final String approverDepartment;
        private LocalDateTime createdAt; // 결재일

        /**
         * @param canViewPrivate 신청자 본인 또는 관리자의 사유/연차 snapshot 조회 권한
         */
        public static LeaveRequestDetailResponse from(LeaveRequest lr, boolean canViewPrivate) {
            Employee emp = lr.getEmployee();
            Employee mgr = lr.getManager(); // null 가능

            return LeaveRequestDetailResponse.builder()
                    // 휴가자
                    .employeeNumber(emp.getEmployeeNumber())
                    .employeeName(emp.getName())
                    .position(emp.getPosition())
                    .department(emp.getDepartmentName())
                    .team(emp.getTeamName())
                    // 휴가 정보
                    .leaveType(lr.getLeaveType())
                    .startDate(lr.getStartDate())
                    .endDate(lr.getEndDate())
                    .useDays(lr.getUseDays())
                    .prevTotalLeaveDays(canViewPrivate ? lr.getPrevTotalLeaveDays() : null)
                    .currTotalLeaveDays(canViewPrivate ? lr.getCurrTotalLeaveDays() : null)
                    .status(lr.getStatus())
                    .leaveReason(canViewPrivate ? lr.getLeaveReason() : null)
                    .rejectReason(canViewPrivate ? lr.getRejectReason() : null)
                    .managedAt(lr.getManagedAt())
                    // 결재자
                    .approverNumber(mgr != null ? mgr.getEmployeeNumber() : null)
                    .approverName(mgr != null ? mgr.getName() : null)
                    .approverPosition(mgr != null ? mgr.getPosition() : null)
                    .approverDepartment(mgr != null ? mgr.getDepartmentName() : null)
                    .createdAt(lr.getCreatedAudit().getCreatedAt()) // 결재일
                    .build();
        }
    }
}