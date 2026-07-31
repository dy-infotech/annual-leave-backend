package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.repository.projection.LeaveRequestStatusCount;

public interface LeaveRequestRepositoryCustom {
	// 승인된 요청의 사용일수 합계 (잔여 연차 계산용)
	Float sumApprovedUseDays(Long employeeId, List<LeaveRequestStatus> status, LocalDate startRange, LocalDate endRange);

	// 특정 상태의 내 요청 개수
    List<LeaveRequestStatusCount> countByStatus(Long employeeId, LocalDate endRange, LocalDate startRange);
    
    // 전직원 기준 특정 상태 요청 개수 (관리자용)
    List<LeaveRequestStatusCount> countByStatus(Collection<String> directTeams, Collection<String> accessibleTeams, LocalDate endRange, LocalDate startRange);

    // 휴가 결재 승인 또는 반려 처리
    int updateLeaveRequest(
            Long requestId,
            Employee approver,
            String rejectReason,
            LeaveRequestStatus sourceStatus,
            LeaveRequestStatus targetStatus,
            LocalDateTime now
    );

    // 검색 기간이 7/1 ~ 7/10이고, 휴가 신청 기간이 7/8 ~ 7/12일 경우, 7/8 ~ 7/10 구간이 겹치니 결과에 포함
    List<LeaveRequest> searchLeaveRequests(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate,
            LeaveRequestStatus status
    );
}
