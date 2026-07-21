package com.dyinfotech.annualleavebackend.service;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.dto.LeaveApprovalDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRejectDto;
import com.dyinfotech.annualleavebackend.dto.PendingLeaveRequestDto;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

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

    public List<PendingLeaveRequestDto.PendingLeaveRequestResponse> getPendingRequests() {
        return leaveRequestRepository.findByStatusOrderByCreatedAtAsc(LeaveRequestStatus.PENDING)
                .stream()
                .map(PendingLeaveRequestDto.PendingLeaveRequestResponse::from)
                .toList();
    }
    
    private Map.Entry<LeaveRequest, Employee> validateLeaveRequest(Long requestId, Long approverId) throws ResponseStatusException {
    	LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 휴가 신청 정보입니다."));
        
        // 요청자와 관리자 정보 추출
        Long employeeId = leaveRequest.getEmployee().getEmployeeId();
        List<Employee> employees = employeeService.getEmployeeList(List.of(employeeId, approverId));
        if (employees.size() < 2) {
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
        Employee employee = null;
        Employee approver = null;
        if (employees.get(0).getEmployeeId().equals(employeeId)) {
        	employee = employees.get(0);
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
    
//    private ConcurrentMap<Long, ReentrantLock> map = new ConcurrentHashMap<>();
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, key = "'active'")
    public LeaveApprovalDto.LeaveApprovalResponse approveLeaveRequest(Long requestId, Long approverId) {
    	
//    	Map.Entry<LeaveRequest, Employee> response = validateLeaveRequest(requestId, approverId);
//    	LeaveRequest leaveRequest = response.getKey();
//    	Employee approver = response.getValue();
//        
//        // 소속 확인 후 승인
//        try {
//            leaveRequest.approve(approver);
//        } catch (IllegalStateException e) {
//            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
//        }
//        
//        Cache employeeCache = cacheManager.getCache(CacheConfig.CACHE_EMPLOYEES);
//        if (employeeCache != null) {
//        	employeeCache.evict(leaveRequest.getEmployee().getEmployeeId());
//        }
//        
//        return LeaveApprovalDto.LeaveApprovalResponse.from(leaveRequest);
    	// 소속 확인 및 기본 검증
        Map.Entry<LeaveRequest, Employee> response = validateLeaveRequest(requestId, approverId);
        LeaveRequest leaveRequest = response.getKey();
        
    	LocalDateTime now = LocalDateTime.now();
        long updatedCount = leaveRequestRepository.updateLeaveRequest(
                requestId,
                approverId,
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
//    	Map.Entry<LeaveRequest, Employee> response = validateLeaveRequest(requestId, approverId);
//    	LeaveRequest leaveRequest = response.getKey();
//    	Employee approver = response.getValue();
//    	
//    	// 소속 확인 후 반려
//        try {
//            leaveRequest.reject(approver, request.getRejectReason());
//        } catch (IllegalStateException e) {
//            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
//        }
//        
//        Cache employeeCache = cacheManager.getCache(CacheConfig.CACHE_EMPLOYEES);
//        if (employeeCache != null) {
//        	employeeCache.evict(leaveRequest.getEmployee().getEmployeeId());
//        }
//
//        return LeaveRejectDto.LeaveRejectResponse.from(leaveRequest);
    	// 소속 확인 및 기본 검증
        Map.Entry<LeaveRequest, Employee> response = validateLeaveRequest(requestId, approverId);
        LeaveRequest leaveRequest = response.getKey();
        
    	LocalDateTime now = LocalDateTime.now();
        long updatedCount = leaveRequestRepository.updateLeaveRequest(
                requestId,
                approverId,
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