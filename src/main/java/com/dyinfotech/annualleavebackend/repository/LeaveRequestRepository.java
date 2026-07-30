package com.dyinfotech.annualleavebackend.repository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.domain.Team;

import io.jsonwebtoken.lang.Collections;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
	
	// 올해의 연도는 Year.now(Clock)를 파라미터로 넘긴다.
	private static LocalDate getStartOfYear(Year year) {
		return year.atDay(1);
	}
	// 올해의 연도는 Year.now(Clock)를 파라미터로 넘긴다.
    private static LocalDate getEndOfYear(Year year) {
        return year.atMonth(Month.DECEMBER).atEndOfMonth();
    }
    
    // 승인된 요청의 사용일수 합계 (잔여 연차 계산용)
    @Query("SELECT COALESCE(SUM(lr.useDays), 0) FROM LeaveRequest lr " +
            "WHERE lr.employee.employeeId = :employeeId AND lr.status IN :status " + 
            "AND lr.startDate BETWEEN :startRange AND :endRange")
    Float sumApprovedUseDays(@Param("employeeId") Long employeeId, @Param("status") List<LeaveRequestStatus> status, @Param("startRange") LocalDate startRange, @Param("endRange") LocalDate endRange);
    default Float sumApprovedUseDays(Long employeeId, Year year) {
    	return sumApprovedUseDays(employeeId, List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING), getStartOfYear(year), getEndOfYear(year));
    }
    default Float sumApprovedUseDays(Long employeeId, Clock clock) {
    	return sumApprovedUseDays(employeeId, Year.now(clock));
    }
    
    // 특정 상태의 내 요청 개수
    long countByEmployee_EmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, 
            LeaveRequestStatus status, 
            LocalDate endRange,   // 위치 주의: LessThanEqual 조건용 (연말)
            LocalDate startRange  // 위치 주의: GreaterThanEqual 조건용 (연초)
    );
    default long countByEmployee_EmployeeIdAndStatus(Long employeeId, LeaveRequestStatus status, Year year) {
    	return countByEmployee_EmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(employeeId, status, getEndOfYear(year), getStartOfYear(year));
    }
    default long countByEmployee_EmployeeIdAndStatus(Long employeeId, LeaveRequestStatus status, Clock clock) {
    	return countByEmployee_EmployeeIdAndStatus(employeeId, status, Year.now(clock));
    }

    // 전직원 기준 특정 상태 요청 개수 (관리자용)
    long countByStatusAndEmployee_TeamInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            LeaveRequestStatus status, 
            List<String> teams, 
            LocalDate endRange,   // 위치 주의: LessThanEqual 조건용 (연말)
            LocalDate startRange  // 위치 주의: GreaterThanEqual 조건용 (연초)
    );
    default long countByStatus(List<Team> teams, LeaveRequestStatus status, Year year) {
    	if (teams == null || teams.isEmpty())	return 0L;
    	return countByStatusAndEmployee_TeamInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(status, teams.stream().map(Team::getTeam).toList(), getEndOfYear(year), getStartOfYear(year));
    }
    default long countByStatus(List<Team> teams,LeaveRequestStatus status, Clock clock) {
    	return countByStatus(teams, status, Year.now(clock));
    }
    
    List<LeaveRequest> findByStatusAndEmployee_TeamInAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtAsc(
            LeaveRequestStatus status, 
            List<String> teams, 
            LocalDate endRange,   // 위치 주의: LessThanEqual 조건용 (연말)
            LocalDate startRange  // 위치 주의: GreaterThanEqual 조건용 (연초)
    );
    default List<LeaveRequest> findByStatusOrderByCreatedAtAsc(List<Team> teams, LeaveRequestStatus status, Year year) {
    	if (teams == null || teams.isEmpty())	return Collections.emptyList();
    	return findByStatusAndEmployee_TeamInAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtAsc(status, teams.stream().map(Team::getTeam).toList(), getEndOfYear(year), getStartOfYear(year));
    }
    default List<LeaveRequest> findByStatusOrderByCreatedAtAsc(List<Team> teams, LeaveRequestStatus status, Clock clock) {
    	return findByStatusOrderByCreatedAtAsc(teams, status, Year.now(clock));
    }

	// 휴가 결재 승인 또는 반려 처리
    // XXX: 낙관적 락(@Version)을 사용해도 되지만 ObjectOptimisticLockingFailureException을 GlobalExceptionHandler에서 처리하는 건
    //		별도 세부 정보를 담을 수 없고 로그 처리도 확실하지 않을 것 같다. 일단 개별 쿼리문으로 대응한다.
	@Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트 자동 클리어
	@Query("UPDATE LeaveRequest lr " +
	       "SET lr.status = :targetStatus, " +
	       "    lr.manager = :approver, " + // lr.manager.employeeId -> lr.manager 로 수정
	       "    lr.managedAt = :now, " +
	       "    lr.rejectReason = :rejectReason " +
	       "WHERE lr.requestId = :requestId " + 
	       "AND lr.status = :sourceStatus")
	int updateLeaveRequest(
	        @Param("requestId") Long requestId, 
	        @Param("approver") Employee approver, // Long approverId -> Employee approver 로 변경
	        @Param("rejectReason") String rejectReason, 
	        @Param("sourceStatus") LeaveRequestStatus sourceStatus, 
	        @Param("targetStatus") LeaveRequestStatus targetStatus, 
	        @Param("now") LocalDateTime now
	);
	
    // 검색 기간이 7/1 ~ 7/10이고, 휴가 신청 기간이 7/8 ~ 7/12일 경우, 7/8 ~ 7/10 구간이 겹치니 결과에 포함
    @Query("SELECT lr FROM LeaveRequest lr " +
            "WHERE (:employeeId IS NULL OR lr.employee.employeeId = :employeeId) " +
            "AND (:startDate IS NULL OR lr.startDate >= :startDate) " +
            "AND (:endDate IS NULL OR lr.endDate <= :endDate) " +
            "AND (:status IS NULL OR lr.status = :status) " +
            "ORDER BY lr.createdAt DESC")
    List<LeaveRequest> searchLeaveRequests(@Param("employeeId") Long employeeId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("status") LeaveRequestStatus status);
}