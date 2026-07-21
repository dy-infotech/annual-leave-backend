package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    // 승인된 요청의 사용일수 합계 (잔여 연차 계산용)
    @Query("SELECT COALESCE(SUM(lr.useDays), 0) FROM LeaveRequest lr " +
            "WHERE lr.employee.employeeId = :employeeId AND lr.status IN ('APPROVED', 'PENDING')")
    Float sumApprovedUseDays(@Param("employeeId") Long employeeId);

    // 특정 상태의 내 요청 개수
    long countByEmployee_EmployeeIdAndStatus(Long employeeId, LeaveRequestStatus status);

    // 전직원 기준 특정 상태 요청 개수 (관리자용)
    long countByStatus(LeaveRequestStatus status);

    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveRequestStatus status);

	// 휴가 결재 승인 또는 반려 처리
    // XXX: 낙관적 락(@Version)을 사용해도 되지만 ObjectOptimisticLockingFailureException울 GlobalExceptionHandler에서 처리하는 건
    //		별도 세부 정보를 담을 수 없고 로그 처리도 확실하지 않을 것 같다. 일단 개별 쿼리문으로 대응한다.
	@Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트 자동 클리어
    @Query("UPDATE LeaveRequest lr " +
           "SET lr.status = :targetStatus, lr.manager.employeeId = :approverId, lr.managedAt = :now, lr.rejectReason = :rejectReason " +
           "WHERE (lr.requestId = :requestId) " + 
           "AND (lr.status = :sourceStatus) ")
    long updateLeaveRequest(@Param("requestId") Long requestId, @Param("approverId") Long approverId, @Param("rejectReason") String rejectReason, @Param("sourceStatus") LeaveRequestStatus sourceStatus, @Param("targetStatus") LeaveRequestStatus targetStatus, @Param("now") LocalDateTime now);

    // 검색 기간이 7/1 ~ 7/10이고, 휴가 신청 기간이 7/8 ~ 7/12일 경우, 7/8 ~ 7/10 구간이 겹치니 결과에 포함
    @Query("SELECT lr FROM LeaveRequest lr " +
            "WHERE (:employeeId IS NULL OR lr.employee.employeeId = :employeeId) " +
            "AND (:startDate IS NULL OR lr.endDate >= :startDate) " +
            "AND (:endDate IS NULL OR lr.startDate <= :endDate) " +
            "AND (:status IS NULL OR lr.status = :status) " +
            "ORDER BY lr.createdAt DESC")
    List<LeaveRequest> searchLeaveRequests(@Param("employeeId") Long employeeId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("status") LeaveRequestStatus status);
}