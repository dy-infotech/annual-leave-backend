package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.util.DateUtils;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.dto.LeaveApprovalDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRejectDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestListDto;
import com.dyinfotech.annualleavebackend.dto.PendingLeaveRequestDto;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

import io.jsonwebtoken.lang.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveApprovalService {
	private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeService employeeService;
    private final TeamService teamService;
    
    private final CacheManager cacheManager;
    
    private final Clock clock;

    public List<PendingLeaveRequestDto.PendingLeaveRequestResponse> getPendingRequests(Long employeeId) {
    	List<Employee> employeeList = employeeService.getEmployeeList(List.of(employeeId));
    	if (employeeList.isEmpty()) {
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다.");
    	}

    	Long excludeId = employeeId;
    	Set<String> directTeams = new HashSet<>();
    	Set<TeamManager> accessibleTeams = new HashSet<>();
    	for (TeamManager team : employeeList.get(0).getTeams()) {
    		String myTeam = team.getTeam().getTeamName();
    		directTeams.add(myTeam);
    		if (myTeam.equals(team.getParentTeam().getTeamName())) {
    			excludeId = null;	// 최상위 팀이면 제외할 필요 없음 (스스로 승인이 가능하므로)
    		}
    		accessibleTeams.addAll(teamService.getSelfAndDescendants(myTeam));
    	}
    	Set<Long> childTeamProjectManagerIds = accessibleTeams.stream()
//    														// 내 팀 + 하위 팀 조합에서 내 팀만 제외하면 하위 팀만 조회
//    														.filter(e -> !directTeams.contains(e.getTeam()))
											    			// 최상위 팀(TeamName == ParentTeamName)과 내가 관리하는 팀을 제외하고, 하위 팀들을 반환
															.filter(e -> !e.getTeam().equals(e.getParentTeam()) && directTeams.contains(e.getParentTeam().getTeamName()))
    														.map(TeamManager::getProjectManagerId)
    												        .filter(Objects::nonNull)
    														.collect(Collectors.toSet());
    	
        return leaveRequestRepository.findByStatusOrderByCreatedAtAsc(excludeId, directTeams, childTeamProjectManagerIds, LeaveRequestStatus.PENDING, clock)
                .stream()
                .map(PendingLeaveRequestDto.PendingLeaveRequestResponse::from)
                .toList();
    }
    
    private Set<String> getAccessibleTeams(List<TeamManager> teams) {
    	if (teams.isEmpty()) {
    		return Collections.emptySet();
    	}
    	
    	return teams.stream()
    				.flatMap(e -> teamService.getSelfAndDescendants(e.getTeam().getTeamName())
    										.stream())
    				.map(e -> e.getTeam().getTeamName())
    				.collect(Collectors.toSet());
    }
    
    @Transactional(readOnly = true)
    public List<LeaveRequestListDto.LeaveRequestListResponse> getApprovedRequests(Long employeeId, String team, String employeeParam) {
    	//승인권자 정보
    	List<Employee> employeeList = employeeService.getEmployeeList(List.of(employeeId));
    	if (employeeList.isEmpty()) {
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다.");
    	}
    	
    	//승인권자의 팀정보
    	Set<String> accessibleTeams = getAccessibleTeams(employeeList.get(0).getTeams());
    	if (accessibleTeams.isEmpty()) {
    		return Collections.emptyList();
    	}
    	
    	//대표이사 계정: 팀별 목록 조회 시 팀정보 유효성 확인 
    	if (team != null && !team.isBlank() && accessibleTeams.contains(team)) {
    		accessibleTeams = Set.of(team);
    	}
    	
    	Year year = Year.now(clock);
    	
        return leaveRequestRepository.searchLeaveRequests(
        		DateUtils.getFirstDayOfYear(year), 
        		DateUtils.getLastDayOfYear(year),
        		LeaveRequestStatus.APPROVED,
        		accessibleTeams,
        		employeeParam
        		)
        		.stream()
                .map(LeaveRequestListDto.LeaveRequestListResponse::from)
                .toList();
    }
    
    public List<LeaveRequestListDto.LeaveRequestListResponse> getRejectedRequests(Long employeeId, String team, String employeeParam) {
    	//승인권자 정보
    	List<Employee> employeeList = employeeService.getEmployeeList(List.of(employeeId));
    	if (employeeList.isEmpty()) {
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다.");
    	}
    	
    	//승인권자의 팀정보
    	Set<String> accessibleTeams = getAccessibleTeams(employeeList.get(0).getTeams());
    	if (accessibleTeams.isEmpty()) {
    		return Collections.emptyList();
    	}
    	
    	//대표이사 계정: 팀별 목록 조회 시 팀정보 유효성 확인 
    	if (team != null && !team.isBlank() && accessibleTeams.contains(team)) {
    		accessibleTeams = Set.of(team);
    	}
    	
    	Year year = Year.now(clock);
    	
        return leaveRequestRepository.searchLeaveRequests(
        		DateUtils.getFirstDayOfYear(year), 
        		DateUtils.getLastDayOfYear(year),
        		LeaveRequestStatus.REJECTED,
        		accessibleTeams,
        		employeeParam
        		)
        		.stream()
                .map(LeaveRequestListDto.LeaveRequestListResponse::from)
                .toList();
    }
    
    private Map.Entry<LeaveRequest, Employee> validateLeaveRequest(Long requestId, Long approverId) throws ResponseStatusException {
    	LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 휴가 신청 정보입니다."));
        
        // 요청자와 관리자 정보 추출
        Long employeeId = leaveRequest.getEmployee().getEmployeeId();
        Set<Long> employeeIds = Stream.of(employeeId, approverId).collect(Collectors.toSet());
        List<Employee> employees = employeeService.getEmployeeList(employeeIds);
        if (employees.size() < employeeIds.size()) {
        	String errorMsg = null;
        	String detailMsg = null;
        	if (employees.isEmpty()) {
        		errorMsg = "존재하지 않는 요청자와 관리자입니다.";
        		detailMsg = "requestId : " + requestId + ",employeeId : " + employeeId + ",approverId : " + approverId;
        	} else {
            	if (employees.get(0).getEmployeeId().equals(employeeId)) {
            		errorMsg = "존재하지 않는 요청자입니다.";
            		detailMsg = "requestId : " + requestId + ",employeeId : " + employeeId;
            	} else {
            		errorMsg = "존재하지 않는 관리자입니다.";
            		detailMsg = "requestId : " + requestId + ",approverId : " + approverId;
            	}
        	}
    		log.error(errorMsg + " " + detailMsg);
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
        }
        
        // 요청자와 관리자 정보 분리
        Employee employee = employees.get(0);
        Employee approver = null;
        if (employees.size() == 1) {	// 대표이사는 스스로 승인할 수 있다.
        	approver = employee;
        } else if (employee.getEmployeeId().equals(employeeId)) {
        	approver = employees.get(1);
        } else {
        	employee = employees.get(1);
        	approver = employees.get(0);
        }
        
        // 관리자가 요청자의 승인자인지 확인 (로그인 시 업데이트되므로 문제되지 않으나 방어코드로 유지한다)
        boolean isApprover = false;
    	StringBuilder approverString = new StringBuilder();
    	for (Long id : teamService.refreshApproverIds(employee)) {
    		if (id.equals(approverId)) {
    			isApprover = true;
    			break;
    		}
    		approverString.append(id).append(",");
    	}
        if (!isApprover) {
        	if (!approverString.isEmpty()) {
        		approverString.setLength(approverString.length() - 1);
        	}
        	
        	String errorMsg = "승인할 수 없는 관리자입니다.";
            String detailMsg = "requestId : " + requestId + ",employeeId : " + employeeId + ",approverId : " + approverId + ",employeeTeam : " + employee.getTeam() + ",expectedApproverId : [" + approverString + "]";
    		log.error(errorMsg + " " + detailMsg);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
        }
        
        return new AbstractMap.SimpleEntry<>(leaveRequest, approver);
    }
    
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, key = "'active'")
    public LeaveApprovalDto.LeaveApprovalResponse approveLeaveRequest(Long requestId, Long approverId) {
    	// 소속 확인 및 기본 검증
        Map.Entry<LeaveRequest, Employee> response = validateLeaveRequest(requestId, approverId);
        LeaveRequest leaveRequest = response.getKey();
        
    	LocalDateTime now = LocalDateTime.now(clock);
        long updatedCount = leaveRequestRepository.updateLeaveRequest(
                requestId,
                response.getValue(),		// approver
                null, // 승인이므로 rejectReason은 null
                LeaveRequestStatus.PENDING,
                LeaveRequestStatus.APPROVED,
                now
        );

        // 업데이트된 행이 0개면 PENDING 상태가 아니라는 의미이므로 예외 발생
        if (updatedCount == 0) {
            log.error("이미 처리된 요청사항입니다. requestId: {}, status: {}", requestId, leaveRequest.getStatus());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "해당 요청은 이미 처리되었습니다.");
        }

        // 캐시 비우기
        Cache employeeCache = cacheManager.getCache(CacheConfig.CACHE_EMPLOYEES);
        if (employeeCache != null) {
            employeeCache.evict(leaveRequest.getEmployee().getEmployeeId());
        }

        // 영속성 컨텍스트가 초기화되었으므로 최신 데이터 재조회 후 응답 생성
        LeaveRequest updatedRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 휴가 신청을 찾을 수 없습니다."));

        return LeaveApprovalDto.LeaveApprovalResponse.from(updatedRequest);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, key = "'active'")
    public LeaveRejectDto.LeaveRejectResponse rejectLeaveRequest(Long requestId, Long approverId, LeaveRejectDto.LeaveRejectRequest request) {
    	// 소속 확인 및 기본 검증
        Map.Entry<LeaveRequest, Employee> response = validateLeaveRequest(requestId, approverId);
        LeaveRequest leaveRequest = response.getKey();
        
    	LocalDateTime now = LocalDateTime.now(clock);
        long updatedCount = leaveRequestRepository.updateLeaveRequest(
                requestId,
                response.getValue(),		// approver
                request.getRejectReason(),
                LeaveRequestStatus.PENDING,
                LeaveRequestStatus.REJECTED,
                now
        );

        // 업데이트된 행이 0개면 PENDING 상태가 아니라는 의미이므로 예외 발생
        if (updatedCount == 0) {
            log.error("이미 처리된 요청사항입니다. requestId: {}, status: {}", requestId, leaveRequest.getStatus());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "해당 요청은 이미 처리되었습니다.");
        }

        // 캐시 비우기
        Cache employeeCache = cacheManager.getCache(CacheConfig.CACHE_EMPLOYEES);
        if (employeeCache != null) {
            employeeCache.evict(leaveRequest.getEmployee().getEmployeeId());
        }

        // 영속성 컨텍스트가 초기화되었으므로 최신 데이터 재조회 후 응답 생성
        LeaveRequest updatedRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 휴가 신청을 찾을 수 없습니다."));

        return LeaveRejectDto.LeaveRejectResponse.from(updatedRequest);
    }
}