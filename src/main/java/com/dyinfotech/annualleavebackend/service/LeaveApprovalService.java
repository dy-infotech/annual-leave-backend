package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.util.DateUtils;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.dto.LeaveApprovalDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRejectDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestListDto;
import com.dyinfotech.annualleavebackend.dto.PendingLeaveRequestDto;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

import io.jsonwebtoken.lang.Collections;
import com.dyinfotech.annualleavebackend.service.TeamService.ManagedTeam;

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
    private final EmployeeCacheInvalidator employeeCacheInvalidator;
    
    private final Clock clock;

    public List<PendingLeaveRequestDto.PendingLeaveRequestResponse> getPendingRequests(Long employeeId) {
        return getPendingRequests(employeeId, 0, MAX_PAGE_SIZE);
    }

    public List<PendingLeaveRequestDto.PendingLeaveRequestResponse> getPendingRequests(
            Long employeeId,
            int page,
            int size) {
        validatePage(page, size);

        List<Employee> employeeList = employeeService.getEmployeeList(List.of(employeeId));
        if (employeeList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다.");
        }

        Long excludeId = employeeId;
        List<ManagedTeam> managedTeams = teamService.findManagedTeams(employeeId);
        Set<String> directTeams = managedTeams.stream()
                .map(ManagedTeam::teamName)
                .collect(Collectors.toSet());

        if (managedTeams.stream().anyMatch(team -> team.teamId().equals(team.parentTeamId()))) {
            excludeId = null;
        }

        Set<ManagedTeam> accessibleTeams = managedTeams.stream()
                .flatMap(team -> teamService.getSelfAndDescendants(team.teamName()).stream())
                .collect(Collectors.toSet());

        Set<Long> childTeamProjectManagerIds = accessibleTeams.stream()
                .filter(team -> !team.teamId().equals(team.parentTeamId()))
                .filter(team -> directTeams.contains(team.parentTeamName()))
                .map(ManagedTeam::projectManagerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Year year = Year.now(clock);
        return leaveRequestRepository.findByStatusAndTeamsInRangePage(
                        excludeId,
                        LeaveRequestStatus.PENDING,
                        directTeams,
                        childTeamProjectManagerIds,
                        DateUtils.getFirstDayOfYear(year),
                        DateUtils.getLastDayOfYear(year),
                        page,
                        size)
                .stream()
                .map(PendingLeaveRequestDto.PendingLeaveRequestResponse::from)
                .toList();
    }

    private Set<String> getAccessibleTeams(Collection<ManagedTeam> teams) {
        if (teams == null || teams.isEmpty()) {
            return Collections.emptySet();
        }
        return teams.stream()
                .flatMap(team -> teamService.getSelfAndDescendants(team.teamName()).stream())
                .map(ManagedTeam::teamName)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestListDto.LeaveRequestListResponse> getApprovedRequests(
            Long employeeId,
            String team,
            String employeeParam) {
        return getApprovedRequests(employeeId, team, employeeParam, 0, MAX_PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestListDto.LeaveRequestListResponse> getApprovedRequests(
            Long employeeId,
            String team,
            String employeeParam,
            int page,
            int size) {
        validatePage(page, size);
        List<Employee> employeeList = employeeService.getEmployeeList(List.of(employeeId));
        if (employeeList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다.");
        }

        Set<String> accessibleTeams = getAccessibleTeams(teamService.findManagedTeams(employeeId));
        if (accessibleTeams.isEmpty()) {
            return Collections.emptyList();
        }
        if (team != null && !team.isBlank()) {
            if (!accessibleTeams.contains(team)) {
                return Collections.emptyList();
            }
            accessibleTeams = Set.of(team);
        }

        Year year = Year.now(clock);
        return leaveRequestRepository.searchLeaveRequestsPage(
                        null,
                        DateUtils.getFirstDayOfYear(year),
                        DateUtils.getLastDayOfYear(year),
                        LeaveRequestStatus.APPROVED,
                        accessibleTeams,
                        employeeParam,
                        page,
                        size)
                .stream()
                .map(LeaveRequestListDto.LeaveRequestListResponse::from)
                .toList();
    }

    public List<LeaveRequestListDto.LeaveRequestListResponse> getRejectedRequests(
            Long employeeId,
            String team,
            String employeeParam) {
        return getRejectedRequests(employeeId, team, employeeParam, 0, MAX_PAGE_SIZE);
    }

    public List<LeaveRequestListDto.LeaveRequestListResponse> getRejectedRequests(
            Long employeeId,
            String team,
            String employeeParam,
            int page,
            int size) {
        validatePage(page, size);
        List<Employee> employeeList = employeeService.getEmployeeList(List.of(employeeId));
        if (employeeList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다.");
        }

        Set<String> accessibleTeams = getAccessibleTeams(teamService.findManagedTeams(employeeId));
        if (accessibleTeams.isEmpty()) {
            return Collections.emptyList();
        }
        if (team != null && !team.isBlank()) {
            if (!accessibleTeams.contains(team)) {
                return Collections.emptyList();
            }
            accessibleTeams = Set.of(team);
        }

        Year year = Year.now(clock);
        return leaveRequestRepository.searchLeaveRequestsPage(
                        null,
                        DateUtils.getFirstDayOfYear(year),
                        DateUtils.getLastDayOfYear(year),
                        LeaveRequestStatus.REJECTED,
                        accessibleTeams,
                        employeeParam,
                        page,
                        size)
                .stream()
                .map(LeaveRequestListDto.LeaveRequestListResponse::from)
                .toList();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || page > MAX_PAGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page 범위를 벗어났습니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
    }

    private Map.Entry<LeaveRequest, Employee> validateLeaveRequest(
            LeaveRequest leaveRequest,
            Long approverId) throws ResponseStatusException {
        Long requestId = leaveRequest.getRequestId();

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
        
        if (!approver.isActive(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "퇴사 처리된 관리자는 휴가를 처리할 수 없습니다.");
        }
        
        // 저장된 approver_id는 신뢰하지 않고 최신 TeamManager 캐시 기준으로 결재 권한을 검증한다.
        boolean isApprover = false;
    	StringBuilder approverString = new StringBuilder();
    	for (Long id : teamService.resolveCurrentApproverIds(employee)) {
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
            String detailMsg = "requestId : " + requestId + ",employeeId : " + employeeId + ",approverId : " + approverId + ",employeeTeam : " + employee.getTeamName() + ",expectedApproverId : [" + approverString + "]";
    		log.error(errorMsg + " " + detailMsg);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
        }
        
        return new AbstractMap.SimpleEntry<>(leaveRequest, approver);
    }
    
    @Transactional
    public LeaveApprovalDto.LeaveApprovalResponse approveLeaveRequest(Long requestId, Long approverId) {
        LeaveRequest current = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 휴가 신청 정보입니다."));

        // idempotency보다 현재 결재권을 먼저 검증한다.
        Map.Entry<LeaveRequest, Employee> response = validateLeaveRequest(current, approverId);
        if (isSameApprovalResult(current, approverId)) {
            return LeaveApprovalDto.LeaveApprovalResponse.from(current);
        }

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
            LeaveRequest replayed = leaveRequestRepository.findById(requestId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 휴가 신청을 찾을 수 없습니다."));
            if (isSameApprovalResult(replayed, approverId)) {
                return LeaveApprovalDto.LeaveApprovalResponse.from(replayed);
            }
            log.error("이미 처리된 요청사항입니다. requestId: {}, status: {}", requestId, replayed.getStatus());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "해당 요청은 이미 처리되었습니다.");
        }

        employeeCacheInvalidator.afterEmployeeViewChange(leaveRequest.getEmployee().getEmployeeId());

        // 영속성 컨텍스트가 초기화되었으므로 최신 데이터 재조회 후 응답 생성
        LeaveRequest updatedRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 휴가 신청을 찾을 수 없습니다."));

        return LeaveApprovalDto.LeaveApprovalResponse.from(updatedRequest);
    }

    @Transactional
    public LeaveRejectDto.LeaveRejectResponse rejectLeaveRequest(Long requestId, Long approverId, LeaveRejectDto.LeaveRejectRequest request) {
        LeaveRequest current = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 휴가 신청 정보입니다."));

        // idempotency보다 현재 결재권을 먼저 검증한다.
        Map.Entry<LeaveRequest, Employee> response = validateLeaveRequest(current, approverId);
        if (isSameRejectionResult(current, approverId, request.getRejectReason())) {
            return LeaveRejectDto.LeaveRejectResponse.from(current);
        }

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
            LeaveRequest replayed = leaveRequestRepository.findById(requestId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 휴가 신청을 찾을 수 없습니다."));
            if (isSameRejectionResult(replayed, approverId, request.getRejectReason())) {
                return LeaveRejectDto.LeaveRejectResponse.from(replayed);
            }
            log.error("이미 처리된 요청사항입니다. requestId: {}, status: {}", requestId, replayed.getStatus());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "해당 요청은 이미 처리되었습니다.");
        }

        employeeCacheInvalidator.afterEmployeeViewChange(leaveRequest.getEmployee().getEmployeeId());

        // 영속성 컨텍스트가 초기화되었으므로 최신 데이터 재조회 후 응답 생성
        LeaveRequest updatedRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 휴가 신청을 찾을 수 없습니다."));

        return LeaveRejectDto.LeaveRejectResponse.from(updatedRequest);
    }

    private boolean isSameApprovalResult(LeaveRequest leaveRequest, Long approverId) {
        return leaveRequest.getStatus() == LeaveRequestStatus.APPROVED
                && leaveRequest.getManager() != null
                && Objects.equals(leaveRequest.getManager().getEmployeeId(), approverId);
    }

    private boolean isSameRejectionResult(LeaveRequest leaveRequest, Long approverId, String rejectReason) {
        return leaveRequest.getStatus() == LeaveRequestStatus.REJECTED
                && leaveRequest.getManager() != null
                && Objects.equals(leaveRequest.getManager().getEmployeeId(), approverId)
                && Objects.equals(leaveRequest.getRejectReason(), rejectReason);
    }
}