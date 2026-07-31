package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.config.CommonConfig;
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

    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	public void isValidDate(LocalDate startDate, LocalDate endDate) throws ResponseStatusException {
		if (startDate != null && startDate.isBefore(CommonConfig.COMPANY_ANNIVERSARY.toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회사 창립 기념일보다 이전 날짜를 요청할 수 없습니다. 요청한 시작일: " + startDate.format(YYYY_MM_DD) + ", 창립기념일: " + CommonConfig.COMPANY_ANNIVERSARY.toLocalDate().format(YYYY_MM_DD));
		}
		if (endDate != null && endDate.isAfter(Year.now(clock).plusYears(1).atMonth(Month.DECEMBER).atEndOfMonth())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내년 이후의 날짜를 지정할 수 없습니다. 요청한 종료일: " + endDate.format(YYYY_MM_DD));
		}
	}
}
