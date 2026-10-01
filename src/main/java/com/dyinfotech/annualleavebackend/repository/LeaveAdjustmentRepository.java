package com.dyinfotech.annualleavebackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.LeaveAdjustment;
import com.dyinfotech.annualleavebackend.repository.query.LeaveAdjustmentRepositoryCustom;

public interface LeaveAdjustmentRepository extends JpaRepository<LeaveAdjustment, LeaveAdjustment.LeaveAdjustmentId>, LeaveAdjustmentRepositoryCustom {
	// XXX: Employee의 List<LeaveAdjustment> leaveAdjustments 를 대체한다
	List<LeaveAdjustment> findAllByEmployeeIdAndYear(Long employeeId, String year);
}