package com.dyinfotech.annualleavebackend.repository;

import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.domain.LeaveRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    // 승인된 요청의 사용일수 합계 (잔여 연차 계산용)
    @Query("SELECT COALESCE(SUM(lr.useDays), 0) FROM LeaveRequest lr " +
            "WHERE lr.employee.employeeId = :employeeId AND lr.status = 'APPROVED'")
    BigDecimal sumApprovedUseDays(@Param("employeeId") Long employeeId);

    // 특정 상태의 내 요청 개수
    long countByEmployee_EmployeeIdAndStatus(Long employeeId, LeaveRequestStatus status);

    // 전직원 기준 특정 상태 요청 개수 (관리자용)
    long countByStatus(LeaveRequestStatus status);

    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveRequestStatus status);

    // 검색 기간이 7/1 ~ 7/10이고, 휴가 신청 기간이 7/8 ~ 7/12일 경우, 7/8 ~ 7/10 구간이 겹치니 결과에 포함
    @Query("SELECT lr FROM LeaveRequest lr " +
            "WHERE (:startDate IS NULL OR lr.endDate >= :startDate) " +
            "AND (:endDate IS NULL OR lr.startDate <= :endDate) " +
            "AND (:status IS NULL OR lr.status = :status) " +
            "ORDER BY lr.createdAt DESC")
    List<LeaveRequest> searchLeaveRequests(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("status") LeaveRequestStatus status);
}