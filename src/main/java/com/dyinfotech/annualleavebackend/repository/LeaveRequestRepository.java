package com.dyinfotech.annualleavebackend.repository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.repository.projection.LeaveRequestStatusCount;
import com.dyinfotech.annualleavebackend.repository.query.LeaveRequestRepositoryCustom;

import io.jsonwebtoken.lang.Collections;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long>, LeaveRequestRepositoryCustom {
	// 올해의 연도는 Year.now(Clock)를 파라미터로 넘긴다.
	private static LocalDate getStartOfYear(Year year) {
		return year.atDay(1);
	}
	// 올해의 연도는 Year.now(Clock)를 파라미터로 넘긴다.
    private static LocalDate getEndOfYear(Year year) {
        return year.atMonth(Month.DECEMBER).atEndOfMonth();
    }
    

    // 승인된 요청의 사용일수 합계 (잔여 연차 계산용)
    default float sumApprovedUseDays(Long employeeId, Year year) {
    	return sumApprovedUseDays(employeeId, List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING), getStartOfYear(year), getEndOfYear(year));
    }
    default float sumApprovedUseDays(Long employeeId, Clock clock) {
    	return sumApprovedUseDays(employeeId, Year.now(clock));
    }
    default Map<Long, Float> sumApprovedUseDays(Collection<Long> employeeIds, Year year) {
    	return sumApprovedUseDays(employeeIds, List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING), getStartOfYear(year), getEndOfYear(year));
    }
    default Map<Long, Float> sumApprovedUseDays(Collection<Long> employeeIds, Clock clock) {
    	return sumApprovedUseDays(employeeIds, Year.now(clock));
    }

    // 특정 상태의 내 요청 개수
    default List<LeaveRequestStatusCount> countByStatus(Long employeeId, Year year) {
    	return countByStatus(employeeId, getEndOfYear(year), getStartOfYear(year));
    }
    default List<LeaveRequestStatusCount> countByStatus(Long employeeId, Clock clock) {
    	return countByStatus(employeeId, Year.now(clock));
    }

    // 전직원 기준 특정 상태 요청 개수 (관리자용)
	default List<LeaveRequestStatusCount> countByStatus(Long excludeId, Collection<String> directTeams, Collection<Team> accessibleTeams, Year year) {
		if (directTeams == null || directTeams.isEmpty() || accessibleTeams == null || accessibleTeams.isEmpty()) {
			return Collections.emptyList();
		}
		return countByStatus(excludeId, directTeams, accessibleTeams, getEndOfYear(year), getStartOfYear(year));
	}
	default List<LeaveRequestStatusCount> countByStatus(Long excludeId, Collection<String> directTeams, Collection<Team> accessibleTeams, Clock clock) {
		return countByStatus(excludeId, directTeams, accessibleTeams, Year.now(clock));
	}

	// 승인 대기 상태 휴가 조회 (관리자용)
    default List<LeaveRequest> findByStatusOrderByCreatedAtAsc(Long excludeId, Collection<String> directTeams, Collection<Long> childTeamProjectManagerIds, LeaveRequestStatus status, Year year) {
    	if (directTeams == null || directTeams.isEmpty()) {
    		return Collections.emptyList();
    	}
    	return findByStatusAndTeamsInRange(excludeId, status, directTeams, childTeamProjectManagerIds, getEndOfYear(year), getStartOfYear(year));
    }
    default List<LeaveRequest> findByStatusOrderByCreatedAtAsc(Long excludeId, Collection<String> directTeams, Collection<Long> childTeamProjectManagerIds, LeaveRequestStatus status, Clock clock) {
    	return findByStatusOrderByCreatedAtAsc(excludeId, directTeams, childTeamProjectManagerIds, status, Year.now(clock));
    }

    @Query("SELECT lr FROM LeaveRequest lr " +
            "JOIN FETCH lr.employee " +
            "LEFT JOIN FETCH lr.manager " +   // manager가 null일 경우에 대비해 LEFT
            "WHERE lr.requestId = :requestId")
    Optional<LeaveRequest> findDetailById(@Param("requestId") Long requestId);

    // 휴가 결재 승인 또는 반려 처리
    // XXX: 낙관적 락(@Version)을 사용해도 되지만 ObjectOptimisticLockingFailureException을 GlobalExceptionHandler에서 처리하는 건
    //		별도 세부 정보를 담을 수 없고 로그 처리도 확실하지 않을 것 같다. 일단 개별 쿼리문으로 대응한다.
//	@Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트 자동 클리어
//	@Query("UPDATE LeaveRequest lr " +
//	       "SET lr.status = :targetStatus, " +
//	       "    lr.manager = :approver, " + // lr.manager.employeeId -> lr.manager 로 수정
//	       "    lr.managedAt = :now, " +
//	       "    lr.rejectReason = :rejectReason " +
//	       "WHERE lr.requestId = :requestId " + 
//	       "AND lr.status = :sourceStatus")
//	int updateLeaveRequest(
//	        @Param("requestId") Long requestId, 
//	        @Param("approver") Employee approver, // Long approverId -> Employee approver 로 변경
//	        @Param("rejectReason") String rejectReason, 
//	        @Param("sourceStatus") LeaveRequestStatus sourceStatus, 
//	        @Param("targetStatus") LeaveRequestStatus targetStatus, 
//	        @Param("now") LocalDateTime now
//	);
	
    // 검색 기간이 7/1 ~ 7/10이고, 휴가 신청 기간이 7/8 ~ 7/12일 경우, 7/8 ~ 7/10 구간이 겹치니 결과에 포함
//    @Query("SELECT lr FROM LeaveRequest lr " +
//            "WHERE (:employeeId IS NULL OR lr.employee.employeeId = :employeeId) " +
//            "AND (:startDate IS NULL OR lr.startDate >= :startDate) " +
//            "AND (:endDate IS NULL OR lr.endDate <= :endDate) " +
//            "AND (:status IS NULL OR lr.status = :status) " +
//            "ORDER BY lr.createdAt DESC")
//    List<LeaveRequest> searchLeaveRequests(@Param("employeeId") Long employeeId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("status") LeaveRequestStatus status);
}