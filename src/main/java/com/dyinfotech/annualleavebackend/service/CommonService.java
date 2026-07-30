package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommonService {

	private final LeaveRequestRepository leaveRequestRepository;
	private final EmployeeLeaveService employeeLeaveService;
	
	private final Clock clock;
	
	public Float getRemainingDays(Employee employee, float currTotalLeaveDays, Float usedDays) {
        return currTotalLeaveDays + employeeLeaveService.getAdjustedLeaveDays(employee.getEmployeeId(), employee.getCurrYear()) - usedDays;
	}

	public Float getRemainingDays(Employee employee, Float usedDays) {
        return getRemainingDays(employee, employeeLeaveService.getCalculatedCurrYearLeaveDays(employee), usedDays);
	}

	public Float getRemainingDays(Employee employee) {
		Float usedDays = leaveRequestRepository.sumApprovedUseDays(employee.getEmployeeId(), clock);
		return getRemainingDays(employee, employee.getCurrTotalLeaveDays(), usedDays);
	}
}
