package com.dyinfotech.annualleavebackend.controller;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.dto.LeaveApprovalDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRejectDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestListDto;
import com.dyinfotech.annualleavebackend.dto.PendingLeaveRequestDto;
import com.dyinfotech.annualleavebackend.service.LeaveApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "관리자 전용 - 휴가 승인 관리", description = "조회/승인/반려 등 휴가 처리 API")
@RestController
@RequestMapping("/api/admin/leave-requests")
@RequiredArgsConstructor
public class LeaveApprovalController {

    private final LeaveApprovalService leaveApprovalService;

    @Operation(summary = "승인 대기 상태 휴가 조회", description = "관리자가 승인 대기 상태인 전체 휴가 정보를 조회한다.")
    @GetMapping("/pending")
    public List<PendingLeaveRequestDto.PendingLeaveRequestResponse> getPendingRequests(@AuthenticationPrincipal EmployeePrincipal principal) {
        return leaveApprovalService.getPendingRequests(principal.employeeId());
    }
    
    @Operation(summary = "승인 상태 휴가 조회", description = "관리자가 하위팀의 승인 상태인 전체 휴가 정보를 조회한다.")
    @GetMapping("/approved")
    public List<LeaveRequestListDto.LeaveRequestListResponse> getApprovedRequests(@RequestParam(value = "team", required = false) String team, 
    		@RequestParam(value = "employeeParam", required = false) String employeeParam, @AuthenticationPrincipal EmployeePrincipal principal) {
        return leaveApprovalService.getApprovedRequests(principal.employeeId(), team, employeeParam);
    }
    
    @Operation(summary = "반려 상태 휴가 조회", description = "관리자가 하위팀의 반려 상태인 전체 휴가 정보를 조회한다.")
    @GetMapping("/rejected")
    public List<LeaveRequestListDto.LeaveRequestListResponse> getRejectedRequests(@RequestParam(value = "team", required = false) String team, 
    		@RequestParam(value = "employeeParam", required = false) String employeeParam, @AuthenticationPrincipal EmployeePrincipal principal) {
        return leaveApprovalService.getRejectedRequests(principal.employeeId(), team, employeeParam);
    }

    @Operation(summary = "휴가 승인", description = "관리자가 휴가 요청을 승인한다.")
    @PostMapping("/{requestId}/approve")
    public LeaveApprovalDto.LeaveApprovalResponse approveLeaveRequest(@PathVariable("requestId") Long requestId, @AuthenticationPrincipal EmployeePrincipal principal) {
        return leaveApprovalService.approveLeaveRequest(requestId, principal.employeeId());
    }

    @Operation(summary = "휴가 반려", description = "관리자가 휴가 요청을 반려한다.")
    @PostMapping("/{requestId}/reject")
    public LeaveRejectDto.LeaveRejectResponse rejectLeaveRequest(@PathVariable("requestId") Long requestId, @AuthenticationPrincipal EmployeePrincipal principal, @Valid @RequestBody LeaveRejectDto.LeaveRejectRequest request) {
        return leaveApprovalService.rejectLeaveRequest(requestId, principal.employeeId(), request);
    }
}
