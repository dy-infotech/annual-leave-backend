package com.dyinfotech.annualleavebackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.LeaveAdjustment;
import com.dyinfotech.annualleavebackend.repository.query.LeaveAdjustmentRepositoryCustom;

public interface LeaveAdjustmentRepository extends JpaRepository<LeaveAdjustment, LeaveAdjustment.LeaveAdjustmentId>, LeaveAdjustmentRepositoryCustom {
//	@Query("SELECT COALESCE(SUM(CASE WHEN la.sign = :plusSign " +
//	   "THEN la.leaveDays ELSE -la.leaveDays END), 0) " +
//	   "FROM LeaveAdjustment la WHERE la.employeeId = :employeeId AND la.year = :year")
//	Float sumAdjustedLeaveDays(@Param("employeeId") Long employeeId, @Param("year") String year, @Param("plusSign") String plusSign);
	
	// XXX: Employee의 List<LeaveAdjustment> leaveAdjustments 를 대체한다
	List<LeaveAdjustment> findAllByEmployeeIdAndYear(Long employeeId, String year);
}