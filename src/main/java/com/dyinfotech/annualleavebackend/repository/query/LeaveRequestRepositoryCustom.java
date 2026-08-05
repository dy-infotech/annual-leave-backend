package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.repository.projection.LeaveRequestStatusCount;

public interface LeaveRequestRepositoryCustom {
	// 승인된 요청의 사용일수 합계 (잔여 연차 계산용)
	float sumApprovedUseDays(Long employeeId, List<LeaveRequestStatus> status, LocalDate startRange, LocalDate endRange);
	Map<Long, Float> sumApprovedUseDays(Collection<Long> employeeIds, List<LeaveRequestStatus> status, LocalDate startRange, LocalDate endRange);

	// 특정 상태의 내 요청 개수
    List<LeaveRequestStatusCount> countByStatus(Long employeeId, LocalDate endRange, LocalDate startRange);
    
    // 전직원 기준 특정 상태 요청 개수 (관리자용)
    List<LeaveRequestStatusCount> countByStatus(Long excludeId, Collection<String> directTeams, Collection<Team> accessibleTeams, LocalDate endRange, LocalDate startRange);

    // 승인 대기 상태 휴가 조회 (관리자용)
    List<LeaveRequest> findByStatusAndTeamsInRange(
    		Long excludeId, 
    		LeaveRequestStatus status, 
    		Collection<String> directTeams, 
    		Collection<Long> childTeamProjectManagerIds, 
    		LocalDate endRange, 
    		LocalDate startRange
    );
    
    // 휴가 결재 승인 또는 반려 처리
    int updateLeaveRequest(
            Long requestId,
            Employee approver,
            String rejectReason,
            LeaveRequestStatus sourceStatus,
            LeaveRequestStatus targetStatus,
            LocalDateTime now
    );
    
    // 공통 조회 코드
    List<LeaveRequest> searchLeaveRequests(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate,
            LeaveRequestStatus status,
            Collection<String> team,
            String searchEmployeeParam
    );

    // 검색 기간이 7/1 ~ 7/10이고, 휴가 신청 기간이 7/8 ~ 7/12일 경우, 7/8 ~ 7/10 구간이 겹치니 결과에 포함
    default List<LeaveRequest> searchLeaveRequests(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate,
            LeaveRequestStatus status
    ) {
    	return searchLeaveRequests(employeeId, startDate, endDate, status, null, null);
    }
    
    // 팀 배열(타겟팀 하위 전체 팀 목록)에 포함된 모든 요청 목록정보를 조회
	default List<LeaveRequest> searchLeaveRequests(
			LocalDate startDate, 
			LocalDate endDate, 
			LeaveRequestStatus status,
			Collection<String> teams
	) {
    	return searchLeaveRequests(null, startDate, endDate, status, teams, null);
	}
	
	// 사번 또는 사원명 조회조건 추가
	default List<LeaveRequest> searchLeaveRequests(
			LocalDate startDate, 
			LocalDate endDate, 
			LeaveRequestStatus status,
			Collection<String> teams,
			String employeeParam
	) {
    	return searchLeaveRequests(null, startDate, endDate, status, teams, employeeParam);
	}
	
	
}
