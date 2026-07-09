package com.dyinfotech.annualleavebackend.service;

import org.springframework.stereotype.Service;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommonService {
	static Float getRemainingDays(Employee employee, Float usedDays) {
        return employee.getCurrTotalLeaveDays() + employee.getAdjustedLeaveDays() - usedDays;
	}
}
