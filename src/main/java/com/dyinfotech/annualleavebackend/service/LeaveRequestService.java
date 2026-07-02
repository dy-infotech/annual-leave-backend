package com.dyinfotech.annualleavebackend.service;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public LeaveRequestDto.LeaveRequestCreateResponse createLeaveRequest(Long employeeId, LeaveRequestDto.LeaveRequestCreateRequest request) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        validateDateRange(request.getStartDate(), request.getEndDate());
        validateUseDaysUnit(request.getUseDays());
        validateUseDaysWithinWeekdays(request.getStartDate(), request.getEndDate(), request.getUseDays());
        validateRemainingLeave(employee, request.getUseDays());

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .employee(employee)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .useDays(request.getUseDays())
                .build();

        leaveRequestRepository.save(leaveRequest);

        return LeaveRequestDto.LeaveRequestCreateResponse.from(leaveRequest);
    }

    // 종료일이 시작일보다 빠르면 안 됨
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종료일은 시작일 이후여야 합니다.");
        }
    }

    // 0.5 단위인지 체크
    private void validateUseDaysUnit(BigDecimal useDays) {
        if (useDays.remainder(BigDecimal.valueOf(0.5)).compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용일수는 0.5 단위로 입력해 주세요.");
        }

        if (useDays.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용일수는 0보다 커야 합니다.");
        }
    }

    // 신청 기간의 평일 수를 초과하지 않는지 체크
    private void validateUseDaysWithinWeekdays(LocalDate startDate, LocalDate endDate, BigDecimal useDays) {
        long weekdays = countWeekdays(startDate, endDate);

        if (useDays.compareTo(BigDecimal.valueOf(weekdays)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용일수(" + useDays + "일)가 신청 기간 내 평일 수(" + weekdays + "일)를 초과했습니다.");
        }
    }

    private long countWeekdays(LocalDate startDate, LocalDate endDate) {
        long weekdays = 0;
        LocalDate date = startDate;

        while (!date.isAfter(endDate)) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                weekdays++;
            }
            date = date.plusDays(1);
        }

        return weekdays;
    }

    // 잔여 연차를 초과하지 않는지 체크
    private void validateRemainingLeave(Employee employee, BigDecimal useDays) {
        BigDecimal usedDays = leaveRequestRepository.sumApprovedUseDays(employee.getEmployeeId());
        BigDecimal remainingDays = employee.getTotalLeaveDays().subtract(usedDays);

        if (useDays.compareTo(remainingDays) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잔여 연차(" + remainingDays + "일)를 초과했습니다.");
        }
    }
}