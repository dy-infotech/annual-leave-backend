package com.dyinfotech.annualleavebackend.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.LeaveType;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Holiday;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestListDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.HolidayRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;
    private final EmployeeLeaveService employeeLeaveService;
    private final NotificationService notificationService;
    private final CommonService commonService;
    private final TeamService teamService;

    @Transactional
    public LeaveRequestDto.LeaveRequestCreateResponse createLeaveRequest(Long employeeId, LeaveRequestDto.LeaveRequestCreateRequest request) {
        LeaveType leaveType = LeaveType.fromName(request.getLeaveType());
        // XXX: 클라에서 받은 정보의 LeaveType을 검증한다.
        if (leaveType == null) {
        	String errorMsg = "LeaveRequest::createLeaveRequest LeaveType 에러. employeeId : " + employeeId + ",leaveType : " + request.getLeaveType();
        	log.error(errorMsg);
        	throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "휴가유형 파라미터가 잘못되었습니다.");
        }
    	
    	Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        String currentYear = String.valueOf(LocalDate.now().getYear());
        // 현재 연도를 currYear에 설정
        if (employee.getCurrYear() != null && !employee.getCurrYear().equals(currentYear)) {
			// 연도가 바뀌었으므로 이전 연도 데이터로 이동
        	employee.setPrevYear(employee.getCurrYear());
        	employee.setPrevYearLeaveDays(employee.getCurrTotalLeaveDays());
        	employee.setCurrYear(currentYear);
		}
        
        // 현재 연도 연차일수 계산 및 설정
        float calculatedCurrYearLeaveDays = employeeLeaveService.getCalculatedCurrYearLeaveDays(employee);
        if (employee.getCurrTotalLeaveDays() != calculatedCurrYearLeaveDays) {        	
        	employee.setCurrYearLeaveDays(calculatedCurrYearLeaveDays);
        }

        validateDateRange(request.getStartDate(), request.getEndDate());
        validateUseDaysUnit(leaveType, request.getUseDays());
        validateUseDaysWithinWeekdays(request.getStartDate(), request.getEndDate(), request.getUseDays());
        validateRemainingLeave(employee, request.getUseDays());

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .employee(employee)
                .leaveType(request.getLeaveType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .useDays(request.getUseDays())
                .build();

        leaveRequestRepository.save(leaveRequest);
        
        boolean hasApproverId = false;
        Set<Long> resolvedApproverIds = teamService.resolveApproverIds(employee);
        for (Long resolvedApproverId : resolvedApproverIds) {
        	if (resolvedApproverId.equals(employee.getApproverId())) {
        		hasApproverId = true;
        		break;
        	}
        }
        if (!hasApproverId && !resolvedApproverIds.isEmpty()) {
        	employee.changeApprover(resolvedApproverIds.iterator().next());
        }

        // 팀 프로젝트 매니저에게 FCM 푸시 알림 전송
        if (!resolvedApproverIds.isEmpty()) {
            notificationService.sendNotificationToTeams(resolvedApproverIds,
                    employee.getEmployeeNumber() + "님의 휴가 신청",
                    "[" + leaveType.getDesc() + "] " + request.getStartDate() + " ~ " + request.getEndDate());
        }

        return LeaveRequestDto.LeaveRequestCreateResponse.from(leaveRequest);
    }

    // 종료일이 시작일보다 빠르면 안 됨
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종료일은 시작일 이후여야 합니다.");
        }
    }

    // 0.5 단위인지 체크
    private void validateUseDaysUnit(LeaveType leaveType, Float useDays) {
    	float remainder = useDays.floatValue() - useDays.intValue();
        if (remainder != 0.0f && remainder != 0.5f) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용일수는 0.5 단위로 입력해 주세요.");
        }

        if (useDays <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용일수는 0보다 커야 합니다.");
        }
        
        // XXX: 반차는 1일씩만 사용하도록 수정
        if ((leaveType.equals(LeaveType.AM_HALF) || leaveType.equals(LeaveType.PM_HALF)) && useDays > 1) {
        	throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "반차는 하루 단위로 사용해야 합니다.");
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
    	// 1. 조회할 연도 목록 추출 (보통 1개 연도이거나 해를 넘기면 2개 연도)
    	Set<String> years = new HashSet<>();
    	years.add(String.valueOf(startDate.getYear()));
    	years.add(String.valueOf(endDate.getYear()));
    	
    	// 2. 해당 연도들의 공휴일 전체 조회
        List<Holiday> holidays = holidayRepository.findAllByYearIn(years);
        
        // 3. LocalDate의 Set으로 변환
        Set<LocalDate> holidayDates = holidays.stream()
                .map(h -> LocalDate.of(
                        Integer.parseInt(h.getYear()), 
                        Integer.parseInt(h.getMonth()), 
                        Integer.parseInt(h.getDay())
                ))
                .collect(Collectors.toSet());
    	
        long weekdays = 0;
        LocalDate date = startDate;
        
        // 4. 주말 및 공휴일 제외 로직 돌리기
        while (!date.isAfter(endDate)) {
        	DayOfWeek dayOfWeek = date.getDayOfWeek();

            if (dayOfWeek != DayOfWeek.SATURDAY && 
                dayOfWeek != DayOfWeek.SUNDAY && 
                !holidayDates.contains(date)) {
            	weekdays += 1.0f;
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
        Float remainingDays = commonService.getRemainingDays(employee, usedDays);

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
    	String detailMsg = "requestId : " + requestId + ",employeeId : " + employeeId;
        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> {
                	String errorMsg = "존재하지 않는 휴가 신청 정보입니다.";
                	log.error(errorMsg + " " + detailMsg);
                	return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
                });

        // 본인 신청이 아닐 경우 취소 불가
        if (!leaveRequest.getEmployee().getEmployeeId().equals(employeeId)) {
        	String errorMsg = "본인의 휴가 신청만 취소할 수 있습니다.";
        	log.error(errorMsg + " " + detailMsg);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, errorMsg);
        }

        try {
            leaveRequest.cancel();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}