package com.dyinfotech.annualleavebackend.service;

import org.springframework.stereotype.Service;

import com.dyinfotech.annualleavebackend.domain.Employee;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommonService {
	private final EmployeeLeaveService employeeLeaveService;
	
	public Float getRemainingDays(Employee employee, float currTotalLeaveDays, Float usedDays) {
        return currTotalLeaveDays + employeeLeaveService.getAdjustedLeaveDays(employee.getEmployeeId(), employee.getCurrYear()) - usedDays;
	}
	public Float getRemainingDays(Employee employee, Float usedDays) {
        return getRemainingDays(employee, employeeLeaveService.getCalculatedCurrYearLeaveDays(employee), usedDays);
	}
}
