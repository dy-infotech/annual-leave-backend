package com.dyinfotech.annualleavebackend.service;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.dto.LeaveApprovalDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRejectDto;
import com.dyinfotech.annualleavebackend.dto.PendingLeaveRequestDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveApprovalService {
	private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final TeamService teamService;

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
        List<Employee> employees = employeeRepository.findAllByEmployeeIdIn(List.of(employeeId, approverId));
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

    @Transactional
    public LeaveApprovalDto.LeaveApprovalResponse approveLeaveRequest(Long requestId, Long approverId) {
    	Map.Entry<LeaveRequest, Employee> response = validateLeaveRequest(requestId, approverId);
    	LeaveRequest leaveRequest = response.getKey();
    	Employee approver = response.getValue();
        
        // 소속 확인 후 승인
        try {
            leaveRequest.approve(approver);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }

        return LeaveApprovalDto.LeaveApprovalResponse.from(leaveRequest);
    }

    @Transactional
    public LeaveRejectDto.LeaveRejectResponse rejectLeaveRequest(Long requestId, Long approverId, LeaveRejectDto.LeaveRejectRequest request) {
    	Map.Entry<LeaveRequest, Employee> response = validateLeaveRequest(requestId, approverId);
    	LeaveRequest leaveRequest = response.getKey();
    	Employee approver = response.getValue();
    	
    	// 소속 확인 후 반려
        try {
            leaveRequest.reject(approver, request.getRejectReason());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }

        return LeaveRejectDto.LeaveRejectResponse.from(leaveRequest);
    }
}