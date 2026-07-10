package com.dyinfotech.annualleavebackend.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveAdjustment;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestListDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeLeaveService employeeLeaveService;

    @Transactional
    public LeaveRequestDto.LeaveRequestCreateResponse createLeaveRequest(Long employeeId, LeaveRequestDto.LeaveRequestCreateRequest request) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        // 현재 연도 연차일수 계산 및 설정
        employeeLeaveService.calculateAndSetCurrentYearLeaveDays(employee);

        validateDateRange(request.getStartDate(), request.getEndDate());
        validateUseDaysUnit(request.getUseDays());
        validateUseDaysWithinWeekdays(request.getStartDate(), request.getEndDate(), request.getUseDays());
        validateRemainingLeave(employee, request.getUseDays());

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .employee(employee)
                .leaveType("") // TODO: leaveType 설정해야함....
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
    private void validateUseDaysUnit(Float useDays) {
    	float remainder = useDays.floatValue() - useDays.intValue();
        if (remainder != 0.0f && remainder != 0.5f) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용일수는 0.5 단위로 입력해 주세요.");
        }

        if (useDays <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용일수는 0보다 커야 합니다.");
        }
    }

    // 신청 기간의 평일 수를 초과하지 않는지 체크
    private void validateUseDaysWithinWeekdays(LocalDate startDate, LocalDate endDate, Float useDays) {
        long weekdays = countWeekdays(startDate, endDate);

        if ((long)Math.ceil(useDays.doubleValue()) > weekdays) {
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

    // 잔여 휴가 수를 초과하지 않는지 체크
    private void validateRemainingLeave(Employee employee, Float useDays) {
    	// 사용한 휴가 수
        Float usedDays = leaveRequestRepository.sumApprovedUseDays(employee.getEmployeeId());
        // 남은 휴가 수 = 현재 총 휴가 수 + 조정된 휴가 수 - 사용한 휴가 수
        Float remainingDays = CommonService.getRemainingDays(employee, usedDays);

        if (useDays > remainingDays) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잔여 연차(" + remainingDays + "일)를 초과했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestListDto.LeaveRequestListResponse> searchLeaveRequests(LeaveRequestListDto.LeaveRequestListRequest condition) {
        return leaveRequestRepository.searchLeaveRequests(condition.getEmployeeId(), condition.getStartDate(), condition.getEndDate(), condition.getStatus())
                .stream()
                .map(LeaveRequestListDto.LeaveRequestListResponse::from)
                .toList();
    }

    @Transactional
    public void cancel(Long employeeId, Long requestId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 휴가 신청 정보입니다."));

        // 본인 신청이 아닐 경우 취소 불가
        if (!leaveRequest.getEmployee().getEmployeeId().equals(employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 휴가 신청만 취소할 수 있습니다.");
        }

        try {
            leaveRequest.cancel();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}