package com.dyinfotech.annualleavebackend.service;

import org.springframework.stereotype.Service;

import com.dyinfotech.annualleavebackend.domain.Employee;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommonService {
	static Float getRemainingDays(Employee employee, float currTotalLeaveDays, Float usedDays) {
        return currTotalLeaveDays + employee.getAdjustedLeaveDays() - usedDays;
	}
}
