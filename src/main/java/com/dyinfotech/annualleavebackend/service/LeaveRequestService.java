package com.dyinfotech.annualleavebackend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.type.LeaveType;
import com.dyinfotech.annualleavebackend.common.util.DateUtils;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Holiday;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.dto.DashboardDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestDetailDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestListDto;
import com.dyinfotech.annualleavebackend.dto.PageResponseDto;
import com.dyinfotech.annualleavebackend.dto.SpecialDayDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE = 10000;
    private static final long MAX_OFFSET = 20_000L;

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeLeaveService employeeLeaveService;
    private final NotificationService notificationService;
    private final HolidaySyncService holidaySyncService;
    private final CommonService commonService;
    private final TeamService teamService;
    private final CurrentAuthorityService currentAuthorityService;
    private final EmployeeCacheInvalidator employeeCacheInvalidator;
    
    private final Clock clock;

    @Transactional
    public LeaveRequestDto.LeaveRequestCreateResponse createLeaveRequest(
            Long employeeId,
            LeaveRequestDto.LeaveRequestCreateRequest request) {
        return createLeaveRequest(employeeId, request, null);
    }

    @Transactional
    public LeaveRequestDto.LeaveRequestCreateResponse createLeaveRequest(
            Long employeeId,
            LeaveRequestDto.LeaveRequestCreateRequest request,
            String idempotencyKey) {
        LeaveType leaveType = LeaveType.fromName(request.getLeaveType());
        // XXX: 클라에서 받은 정보의 LeaveType을 검증한다.
        if (leaveType == null) {
        	String errorMsg = "LeaveRequest::createLeaveRequest LeaveType 에러. employeeId : " + employeeId + ",leaveType : " + request.getLeaveType();
        	log.error(errorMsg);
        	throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "휴가유형 파라미터가 잘못되었습니다.");
        }
    	
        String normalizedRequestKey = normalizeCreateRequestKey(idempotencyKey);
        String requestHash = normalizedRequestKey == null
                ? null
                : createLeaveRequestHash(employeeId, leaveType, request);

    	Employee employee = employeeRepository.findByIdForUpdate(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        if (normalizedRequestKey != null) {
            var replay = leaveRequestRepository
                    .findByEmployee_EmployeeIdAndCreateRequestKey(
                            employeeId,
                            normalizedRequestKey);
            if (replay.isPresent()) {
                if (!java.util.Objects.equals(
                        replay.get().getCreateRequestHash(),
                        requestHash)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "동일한 Idempotency-Key가 다른 휴가 신청에 사용되었습니다.");
                }
                return LeaveRequestDto.LeaveRequestCreateResponse.from(replay.get());
            }
        }
    	
    	LocalDate today = LocalDate.now(clock);
    	if (!employee.isActive(today)) {
    		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "퇴사 처리된 사원은 휴가를 신청할 수 없습니다.");
    	}
        String currentYear = String.valueOf(today.getYear());
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

        validateDateRange(request.getStartDate(), request.getEndDate(), today, employee.getHireDate(), employee.getFireDate());
        validateUseDaysUnit(leaveType, request.getUseDays());
        validateLeaveReason(leaveType, request.getLeaveReason());
        // 모든 휴가 유형은 신청 기간의 실제 근무일수와 사용일수가 일치해야 한다.
        // 대체/출산/가족돌봄 휴가는 연차 잔여량만 차감/검증 대상에서 제외한다.
        validateUseDaysWithinWeekdays(request.getStartDate(), request.getEndDate(), request.getUseDays());

        // 직원 row lock을 잡은 동안 연간 요청 엔티티를 다시 전부 로드하지 않는다.
        // 같은 SUM 결과를 잔여 검증과 신청 전/후 snapshot 계산에 함께 사용한다.
        Year leaveYear = Year.from(today);
        LocalDate leaveYearStart = leaveYear.atDay(1);
        LocalDate leaveYearEnd = leaveYear.atMonth(Month.DECEMBER).atEndOfMonth();
        float usedAnnualLeaveDays = leaveRequestRepository.sumRequestedUseDays(
                employeeId,
                List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING),
                leaveYearStart,
                leaveYearEnd);
        float realPrevLeaveDays = commonService.getRemainingDays(
                employee,
                employee.getCurrTotalLeaveDays(),
                usedAnnualLeaveDays);

        if (!LeaveType.ALTERNATIVE.equals(leaveType)
                && !LeaveType.PARENTAL.equals(leaveType)
                && !LeaveType.FAMILY.equals(leaveType)) {
            validateRemainingLeave(realPrevLeaveDays, request.getUseDays());
        }
        
        List<LeaveRequestListDto.LeaveRequestListResponse> dataList = 
        		searchLeaveRequests(new LeaveRequestListDto.LeaveRequestListRequest( 
        		                employeeId, 
        		                null,                       // employeeName 자리
        		                request.getStartDate(), 
        		                request.getEndDate(), 
        		                null,                       // status 상태 자리
        		                null         // searchEmployeeParam 검색어 자리
        		            ));
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

        float requestedAnnualLeaveDays = LeaveType.ALTERNATIVE.equals(leaveType)
                || LeaveType.PARENTAL.equals(leaveType)
                || LeaveType.FAMILY.equals(leaveType) ? 0.0f : request.getUseDays();
        // 현재 신청 반영 후 잔여 스냅샷
        float realCurrLeaveDays = realPrevLeaveDays - requestedAnnualLeaveDays;

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .employee(employee)
                .leaveType(request.getLeaveType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .useDays(request.getUseDays())  
                
                .prevTotalLeaveDays(realPrevLeaveDays)  
                .currTotalLeaveDays(realCurrLeaveDays)                 
                
                .leaveReason(request.getLeaveReason())
                .build();

        if (normalizedRequestKey != null) {
            leaveRequest.markCreateRequest(normalizedRequestKey, requestHash);
        }
        leaveRequestRepository.saveAndFlush(leaveRequest);
        employeeCacheInvalidator.afterEmployeeViewChange(employeeId);
 
        // 팀 프로젝트 매니저에게 FCM 푸시 알림 전송
        Set<Long> resolvedApproverIds = teamService.resolveCurrentApproverIds(employee);
        if (!resolvedApproverIds.isEmpty()) {
            Set<Long> notificationApproverIds = Set.copyOf(resolvedApproverIds);
            String notificationTitle = employee.getName() + "님의 휴가 신청";
            String notificationBody =
                    "[" + leaveType.getDesc() + "] " + request.getStartDate() + " ~ " + request.getEndDate();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationService.sendNotificationToTeams(
                            notificationApproverIds,
                            notificationTitle,
                            notificationBody);
                }
            });
        }

        return LeaveRequestDto.LeaveRequestCreateResponse.from(leaveRequest);
    }

    private String normalizeCreateRequestKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }

        String normalized = idempotencyKey.trim();
        if (normalized.length() < 16
                || normalized.length() > 128
                || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String createLeaveRequestHash(
            Long employeeId,
            LeaveType leaveType,
            LeaveRequestDto.LeaveRequestCreateRequest request) {
        String reason = request.getLeaveReason() == null
                ? ""
                : request.getLeaveReason().trim();
        String canonical = employeeId
                + "|" + leaveType.name()
                + "|" + request.getStartDate()
                + "|" + request.getEndDate()
                + "|" + DateUtils.toMinutes(request.getUseDays())
                + "|" + reason.length() + ":" + reason;

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private void validateLeaveReason(LeaveType leaveType, String leaveReason) {
        if (leaveType.requiresReason()
                && (leaveReason == null || leaveReason.isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    leaveType.getDesc() + "는 휴가 사유를 입력해야 합니다.");
        }
    }

    private void validateDateRange(
            LocalDate startDate,
            LocalDate endDate,
            LocalDate today,
            LocalDate hireDate,
            LocalDate fireDate) {
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종료일은 시작일 이후여야 합니다.");
        }
//    	commonService.isValidDate(startDate, endDate);
        Year leaveYear = Year.from(today);
        LocalDate leaveStartDate = leaveYear.atDay(1);
        LocalDate leaveEndDate = leaveYear.atMonth(Month.DECEMBER).atEndOfMonth();

        if (startDate.isBefore(hireDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "입사일 이전에는 휴가를 신청할 수 없습니다.");
        }

        if (fireDate != null && endDate.isAfter(fireDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "퇴사일 이후에는 휴가를 신청할 수 없습니다.");
        }

        if (startDate.isBefore(leaveStartDate) || endDate.isAfter(leaveEndDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "휴가 신청 가능 기간을 벗어났습니다. 가능 기간 : " + leaveStartDate + " ~ " + leaveEndDate);
        }
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
        
        if (leaveType.isHalfLeave() && Float.compare(useDays, 0.5f) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, leaveType.getValidationMessage());
        }
        if (!leaveType.isPartialLeave() && useDays % 1.0f != 0.0f) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, leaveType.getValidationMessage());
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

    // 신청 기간의 실제 근무일수와 요청 사용일수가 일치하는지 체크
    private void validateUseDaysWithinWeekdays(LocalDate startDate, LocalDate endDate, Float useDays) {
        long weekdays = countWeekdays(startDate, endDate);

        if ((long)Math.ceil(useDays.doubleValue()) != weekdays) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용일수(" + useDays + "일)와 신청 기간 내 실제 근무일수(" + weekdays + "일)가 일치하지 않습니다.");
        }
    }
    
    @Transactional(readOnly = true)
    public DashboardDto.LeavePeriodResponse getMyLeavePeriod(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));
        LocalDate today = LocalDate.now(clock);
        Year currentYear = Year.from(today);

        return DashboardDto.LeavePeriodResponse.builder()
                .startDate(currentYear.atDay(1))
                .endDate(currentYear.atMonth(Month.DECEMBER).atEndOfMonth())
                .build();
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
    private void validateRemainingLeave(float remainingDays, Float useDays) {
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
    public List<LeaveRequestListDto.LeaveRequestListResponse> searchLeaveRequests(
            LeaveRequestListDto.LeaveRequestListRequest condition) {
        // 내부 정합성 검사는 전체 결과를 사용한다. 외부 목록 API만 paging 한다.
        return searchLeaveRequests(condition, null, new LeaveVisibility(false, Set.of()));
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestListDto.LeaveRequestListResponse> searchLeaveRequests(
            LeaveRequestListDto.LeaveRequestListRequest condition,
            Long currentEmployeeId) {
        LeaveVisibility visibility = resolveLeaveVisibility(currentEmployeeId);
        return searchLeaveRequests(condition, currentEmployeeId, visibility);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestListDto.LeaveRequestListResponse> searchManagedLeaveRequestsPage(
            LeaveRequestListDto.LeaveRequestListRequest condition,
            Long currentEmployeeId,
            int page,
            int size,
            LocalDateTime cursorRequestedAt,
            Long cursorRequestId) {
        validatePage(page, size);
        validateCursor(cursorRequestedAt, cursorRequestId);
        commonService.isValidDate(condition.getStartDate(), condition.getEndDate());

        Set<String> accessibleTeams = requireAccessibleManagedTeamNames(currentEmployeeId);
        List<LeaveRequest> requests = cursorRequestedAt == null
                ? leaveRequestRepository.searchLeaveRequestsPage(
                        condition.getEmployeeId(),
                        condition.getStartDate(),
                        condition.getEndDate(),
                        condition.getStatus(),
                        accessibleTeams,
                        condition.getSearchEmployeeParam(),
                        page,
                        size)
                : leaveRequestRepository.searchLeaveRequestsCursor(
                        condition.getEmployeeId(),
                        condition.getStartDate(),
                        condition.getEndDate(),
                        condition.getStatus(),
                        accessibleTeams,
                        condition.getSearchEmployeeParam(),
                        cursorRequestedAt,
                        cursorRequestId,
                        size);

        // 이 메서드는 관리자 관리범위로 query 자체가 제한되므로 반환 row의 private 필드 조회를 허용한다.
        return toListResponses(requests, currentEmployeeId, new LeaveVisibility(true, Set.of()));
    }

    private Set<String> requireAccessibleManagedTeamNames(Long managerEmployeeId) {
        Set<String> accessibleTeams = teamService.findManagedTeams(managerEmployeeId).stream()
                .flatMap(team -> teamService.getSelfAndDescendants(team.teamName()).stream())
                .map(TeamService.ManagedTeam::teamName)
                .collect(Collectors.toSet());

        if (accessibleTeams.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "관리 가능한 휴가 신청 범위가 없습니다.");
        }
        return accessibleTeams;
    }

    @Transactional(readOnly = true)
    public PageResponseDto<LeaveRequestListDto.LeaveRequestListResponse> searchLeaveRequestsPage(
            LeaveRequestListDto.LeaveRequestListRequest condition,
            Long currentEmployeeId,
            int page,
            int size) {
        return searchLeaveRequestsPage(condition, currentEmployeeId, page, size, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponseDto<LeaveRequestListDto.LeaveRequestListResponse> searchLeaveRequestsPage(
            LeaveRequestListDto.LeaveRequestListRequest condition,
            Long currentEmployeeId,
            int page,
            int size,
            LocalDateTime cursorRequestedAt,
            Long cursorRequestId) {
        validatePage(page, size);
        validateCursor(cursorRequestedAt, cursorRequestId);
        LeaveVisibility visibility = resolveLeaveVisibility(currentEmployeeId);
        commonService.isValidDate(condition.getStartDate(), condition.getEndDate());

        long totalCount = leaveRequestRepository.countLeaveRequests(
                condition.getEmployeeId(), condition.getStartDate(), condition.getEndDate(),
                condition.getStatus(), null, condition.getSearchEmployeeParam());

        List<LeaveRequest> fetched;
        boolean hasMore;
        if (cursorRequestedAt == null) {
            fetched = leaveRequestRepository.searchLeaveRequestsPage(
                    condition.getEmployeeId(), condition.getStartDate(), condition.getEndDate(),
                    condition.getStatus(), null, condition.getSearchEmployeeParam(),
                    page, size);
            hasMore = ((long) page + 1L) * size < totalCount;
        } else {
            fetched = leaveRequestRepository.searchLeaveRequestsCursor(
                    condition.getEmployeeId(), condition.getStartDate(), condition.getEndDate(),
                    condition.getStatus(), null, condition.getSearchEmployeeParam(),
                    cursorRequestedAt, cursorRequestId, size + 1);
            hasMore = fetched.size() > size;
        }
        List<LeaveRequest> requests = hasMore && fetched.size() > size
                ? fetched.subList(0, size)
                : fetched;

        return new PageResponseDto<>(
                toListResponses(requests, currentEmployeeId, visibility),
                totalCount,
                hasMore);
    }

    private List<LeaveRequestListDto.LeaveRequestListResponse> searchLeaveRequests(
            LeaveRequestListDto.LeaveRequestListRequest condition,
            Long currentEmployeeId,
            LeaveVisibility visibility) {
        commonService.isValidDate(condition.getStartDate(), condition.getEndDate());
        List<LeaveRequest> requests = leaveRequestRepository.searchLeaveRequests(
                condition.getEmployeeId(),
                condition.getStartDate(),
                condition.getEndDate(),
                condition.getStatus(),
                null,
                condition.getSearchEmployeeParam()
        );
        return toListResponses(requests, currentEmployeeId, visibility);
    }

    private List<LeaveRequestListDto.LeaveRequestListResponse> toListResponses(
            List<LeaveRequest> requests,
            Long currentEmployeeId,
            LeaveVisibility visibility) {
        return requests.stream()
                .map(leaveRequest -> LeaveRequestListDto.LeaveRequestListResponse.from(
                        leaveRequest,
                        canViewPrivateLeave(
                                leaveRequest,
                                currentEmployeeId,
                                visibility)))
                .toList();
    }

    private LeaveVisibility resolveLeaveVisibility(Long currentEmployeeId) {
        if (currentEmployeeId == null) {
            return new LeaveVisibility(false, Set.of());
        }

        // 대표이사는 인사권 보유 여부와 별개로 전사 휴가 비공개 필드를 열람할 수 있다.
        if (currentAuthorityService.isCeo(currentEmployeeId)) {
            return new LeaveVisibility(true, Set.of());
        }

        if (!currentAuthorityService.isAdmin(currentEmployeeId)) {
            return new LeaveVisibility(false, Set.of());
        }

        Set<String> accessibleTeams = teamService.findManagedTeams(currentEmployeeId).stream()
                .flatMap(team -> teamService.getSelfAndDescendants(team.teamName()).stream())
                .map(TeamService.ManagedTeam::teamName)
                .collect(Collectors.toSet());
        return new LeaveVisibility(false, accessibleTeams);
    }

    private boolean canViewPrivateLeave(
            LeaveRequest leaveRequest,
            Long currentEmployeeId,
            LeaveVisibility visibility) {
        boolean isOwner = currentEmployeeId != null
                && leaveRequest.getEmployee().getEmployeeId().equals(currentEmployeeId);
        return isOwner
                || visibility.canViewAll()
                || visibility.accessibleTeams().contains(
                        leaveRequest.getEmployee().getTeamName());
    }

    private record LeaveVisibility(
            boolean canViewAll,
            Set<String> accessibleTeams) {
    }

    private void validateCursor(LocalDateTime cursorRequestedAt, Long cursorRequestId) {
        if ((cursorRequestedAt == null) != (cursorRequestId == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cursorRequestedAt과 cursorRequestId는 함께 지정해야 합니다.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || page > MAX_PAGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page 범위를 벗어났습니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
        if ((long) page * size > MAX_OFFSET) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "너무 깊은 페이지는 조회할 수 없습니다. 검색 조건을 좁혀주세요.");
        }
    }

    @Transactional(readOnly = true)
    public LeaveRequestDetailDto.LeaveRequestDetailResponse getLeaveRequestDetail(
            Long requestId,
            Long currentEmployeeId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findDetailById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "휴가 신청을 찾을 수 없습니다."));

        LeaveVisibility visibility = currentAuthorityService
                .canViewAllLeaveDetails(currentEmployeeId)
                        ? new LeaveVisibility(true, Set.of())
                        : resolveLeaveVisibility(currentEmployeeId);
        return LeaveRequestDetailDto.LeaveRequestDetailResponse.from(
                leaveRequest,
                canViewPrivateLeave(
                        leaveRequest,
                        currentEmployeeId,
                        visibility));
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

        if (leaveRequest.getStatus() == LeaveRequestStatus.CANCELLED) {
            return;
        }
        if (leaveRequest.getStatus() != LeaveRequestStatus.PENDING && leaveRequest.getStatus() != LeaveRequestStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "대기 또는 승인 상태인 신청만 취소할 수 있습니다.");
        }

        LocalDate today = LocalDate.now(clock);
        if (leaveRequest.getStatus() == LeaveRequestStatus.APPROVED && !leaveRequest.getStartDate().isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 시작되었거나 지난 휴가는 취소할 수 없습니다.");
        }

        if (leaveRequestRepository.cancelLeaveRequest(requestId, employeeId, today) == 0) {
            LeaveRequest replayed = leaveRequestRepository.findById(requestId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 휴가 신청 정보입니다."));
            if (replayed.getStatus() == LeaveRequestStatus.CANCELLED
                    && replayed.getEmployee().getEmployeeId().equals(employeeId)) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "해당 요청은 이미 처리되었습니다.");
        }

        employeeCacheInvalidator.afterEmployeeViewChange(employeeId);
    }
}