package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.dyinfotech.annualleavebackend.dto.LeaveRequestDetailDto;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.type.LeaveType;
import com.dyinfotech.annualleavebackend.common.util.DateUtils;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Holiday;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestListDto;
import com.dyinfotech.annualleavebackend.dto.SpecialDayDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeLeaveService employeeLeaveService;
    private final NotificationService notificationService;
    private final HolidaySyncService holidaySyncService;
    private final CommonService commonService;
    private final TeamService teamService;
    
    private final Clock clock;

    @Transactional
    @Caching(evict = {
    	    // 1. 해당 직원의 단건 캐시(getMyInfo) 날리기
    	    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, key = "#a0"),
    	    // 2. 재직 중인 직원 전체 목록 캐시('active')도 같이 날리기
    	    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, key = "'active'")
    	})
    public LeaveRequestDto.LeaveRequestCreateResponse createLeaveRequest(Long employeeId, LeaveRequestDto.LeaveRequestCreateRequest request) {
        LeaveType leaveType = LeaveType.fromName(request.getLeaveType());
        // XXX: 클라에서 받은 정보의 LeaveType을 검증한다.
        if (leaveType == null) {
        	String errorMsg = "LeaveRequest::createLeaveRequest LeaveType 에러. employeeId : " + employeeId + ",leaveType : " + request.getLeaveType();
        	log.error(errorMsg);
        	throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "휴가유형 파라미터가 잘못되었습니다.");
        }
    	
    	Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        String currentYear = String.valueOf(LocalDate.now(clock).getYear());
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
        
        List<LeaveRequestListDto.LeaveRequestListResponse> dataList = 
        		searchLeaveRequests(new LeaveRequestListDto.LeaveRequestListRequest(employeeId, request.getStartDate(), request.getEndDate(), null));
        for (LeaveRequestListDto.LeaveRequestListResponse data : dataList) {
        	// 신청과 승인 상태인 경우에만 중복 확인
        	if (!data.getStatus().equals(LeaveRequestStatus.PENDING.name()) && !data.getStatus().equals(LeaveRequestStatus.APPROVED.name())) {
        		continue;
        	}
        	
        	// 기간 겹침 검사
        	boolean notOverlap = request.getStartDate().isAfter(data.getEndDate()) || request.getEndDate().isBefore(data.getStartDate());
        	if (notOverlap) {
        	    continue;
        	}
            
            // 반차 예외 처리
            if (isHalfCombinationAllowed(data, request)) {
                continue;
            }
            
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 신청된 연차 기간과 중복됩니다.");
        }
        
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .employee(employee)
                .leaveType(request.getLeaveType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .useDays(request.getUseDays())
                .leaveReason(request.getLeaveReason())
                .build();

        leaveRequestRepository.save(leaveRequest);

        // 팀 프로젝트 매니저에게 FCM 푸시 알림 전송
        Set<Long> resolvedApproverIds = teamService.refreshApproverIds(employee);
        if (!resolvedApproverIds.isEmpty()) {
            notificationService.sendNotificationToTeams(resolvedApproverIds,
                    employee.getName() + "님의 휴가 신청",
                    "[" + leaveType.getDesc() + "] " + request.getStartDate() + " ~ " + request.getEndDate());
        }

        return LeaveRequestDto.LeaveRequestCreateResponse.from(leaveRequest);
    }

    // 종료일이 시작일보다 빠르면 안 됨
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종료일은 시작일 이후여야 합니다.");
        }
    	commonService.isValidDate(startDate, endDate);
    }
    
//    // 1일과 0.5일 단위만 허용
//    private boolean validateUseDaysUnit(Float useDays) {
//        int useMinutes = (int)(useDays * CommonConfig.DAILY_STANDARD_WORKING_MINUTES);
//        int modMinutes = useMinutes % CommonConfig.DAILY_STANDARD_WORKING_MINUTES;
//        return modMinutes == 0 || modMinutes == CommonConfig.DAILY_STANDARD_WORKING_MINUTES / 2;
//    }
//
//    // 0.5 단위인지 체크
//    private void validateUseDaysUnit(LeaveType leaveType, Float useDays) {
//    	if (!validateUseDaysUnit(useDays)) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용일수는 0.5 단위로 입력해 주세요.");
//        }
//
//        if (useDays <= 0) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용일수는 0보다 커야 합니다.");
//        }
//        
//        // XXX: 반차는 1일씩만 사용하도록 수정
//        if (leaveType.isHalfLeave() && useDays > 1) {
//        	throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "반차는 하루 단위로 사용해야 합니다.");
//        }
//    }
    private void validateUseDaysUnit(LeaveType leaveType, Float useDays) {
        if (useDays == null || useDays <= 0) {
        	throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용일수는 0보다 커야 합니다.");
        }
        
        int useMinutes = DateUtils.toMinutes(useDays);
        if (!leaveType.isValidMinutes(useMinutes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, leaveType.getValidationMessage());
        }
        
        // XXX: 하루 미만 단위 휴가는 신청 1건당 최대 하루까지만 가능 (예시: 반차)
        if (leaveType.isPartialLeave() && useDays > 1.0f) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시간 단위 휴가는 1일을 초과할 수 없습니다.");
        }
    }

    // 신청 기간의 평일 수를 초과하지 않는지 체크
    private void validateUseDaysWithinWeekdays(LocalDate startDate, LocalDate endDate, Float useDays) {
        long weekdays = countWeekdays(startDate, endDate);

        if ((long)Math.ceil(useDays.doubleValue()) > weekdays) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용일수(" + useDays + "일)가 신청 기간 내 평일 수(" + weekdays + "일)를 초과했습니다.");
        }
    }
    
    public List<SpecialDayDto.SpecialDayResponse> getHolidays(int year) {    	
//    	List<Holiday> holidays = holidayRepository.findAllByYear(String.valueOf(year));
//    	
//    	List<SpecialDayDto.SpecialDayResponse> specialDayResponses = new ArrayList<>();
//    	for (Holiday holiday : holidays) {
//    		specialDayResponses.add(SpecialDayDto.SpecialDayResponse.builder()
//							    	    		.name(holiday.getName())
//							    	    		.date(LocalDate.of(Integer.parseInt(holiday.getYear()), 
//							    	    							Integer.parseInt(holiday.getMonth()), 
//							    	    							Integer.parseInt(holiday.getDay())))
//							    	    		.build());
//    	}
//    	return specialDayResponses;
        return holidaySyncService.findAllByYear(year).stream()
        						.map(holiday -> SpecialDayDto.SpecialDayResponse.builder()
				        														.name(holiday.getName())
				        														.date(holiday.getHolidayDate())
				        														.build())
        						.toList();
    }

    private static final Set<DayOfWeek> WEEKENDS = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    private long countWeekdays(LocalDate startDate, LocalDate endDate) {
    	// 해당 연도 사이의 공휴일 전체 조회
        List<Holiday> holidays = holidaySyncService.findByYearRange(startDate.getYear(), endDate.getYear());
        
        // LocalDate의 Set으로 변환
        Set<LocalDate> holidayDates = holidays.stream()
								                .map(Holiday::getHolidayDate)
								                .collect(Collectors.toSet());
    	
        long weekdays = 0;
        LocalDate date = startDate;
        
        // 4. 주말 및 공휴일 제외 로직 돌리기
        while (!date.isAfter(endDate)) {
        	DayOfWeek dayOfWeek = date.getDayOfWeek();

            if (!WEEKENDS.contains(dayOfWeek) && 
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
        float usedDays = leaveRequestRepository.sumApprovedUseDays(employee.getEmployeeId(), clock);
        // 남은 휴가 수 = 현재 총 휴가 수 + 조정된 휴가 수 - 사용한 휴가 수
        float remainingDays = commonService.getRemainingDays(employee, usedDays);

        if (useDays > remainingDays) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잔여 연차(" + remainingDays + "일)를 초과했습니다.");
        }
    }
    
    private boolean isHalfCombinationAllowed(LeaveRequestListDto.LeaveRequestListResponse existing,
           									LeaveRequestDto.LeaveRequestCreateRequest request) {
        LeaveType existingType = LeaveType.fromName(existing.getLeaveType());
        LeaveType requestType = LeaveType.fromName(request.getLeaveType());
        if (existingType == null || requestType == null) {
            return false;
        }

        // 각 요청은 반차 요청이어야 함
        if (!existingType.isHalfLeave() || !requestType.isHalfLeave()) {
            return false;
        }

        // 각 요청은 1일 단위여야 함
        if (!existing.getStartDate().equals(existing.getEndDate())
                || !request.getStartDate().equals(request.getEndDate())) {
            return false;
        }

        // 두 요청이 같은 날이어야 함
        if (!existing.getStartDate().equals(request.getStartDate())) {
            return false;
        }

        // AM + PM만 허용
        return !existingType.equals(requestType);
    }
    
    @Transactional(readOnly = true)
    public List<LeaveRequestListDto.LeaveRequestListResponse> searchLeaveRequests(LeaveRequestListDto.LeaveRequestListRequest condition) {
    	commonService.isValidDate(condition.getStartDate(), condition.getEndDate());
        return leaveRequestRepository.searchLeaveRequests(condition.getEmployeeId(), condition.getStartDate(), condition.getEndDate(), condition.getStatus())
                .stream()
                .map(LeaveRequestListDto.LeaveRequestListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LeaveRequestDetailDto.LeaveRequestDetailResponse getLeaveRequestDetail(Long requestId, Long currentEmployeeId, boolean isAdmin) {

        LeaveRequest leaveRequest = leaveRequestRepository.findDetailById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("휴가 신청을 찾을 수 없습니다. requestId: " + requestId));

        // 본인, 관리자만 사유 조회 권한을 가짐
        boolean isOwner = leaveRequest.getEmployee().getEmployeeId().equals(currentEmployeeId);
        boolean canViewReason = isOwner || isAdmin;

        return LeaveRequestDetailDto.LeaveRequestDetailResponse.from(leaveRequest, canViewReason);
    }

    @Transactional
    @Caching(evict = {
    	    // 1. 해당 직원의 단건 캐시(getMyInfo) 날리기
    	    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, key = "#a0"),
    	    // 2. 재직 중인 직원 전체 목록 캐시('active')도 같이 날리기
    	    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, key = "'active'")
    	})
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
            leaveRequest.cancel(clock);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}