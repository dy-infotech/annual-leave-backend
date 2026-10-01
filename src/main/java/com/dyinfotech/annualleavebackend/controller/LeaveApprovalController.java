package com.dyinfotech.annualleavebackend.controller;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.common.security.ReplayAwareApproval;
import com.dyinfotech.annualleavebackend.dto.LeaveApprovalDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRejectDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestListDto;
import com.dyinfotech.annualleavebackend.dto.PendingLeaveRequestDto;
import com.dyinfotech.annualleavebackend.dto.PageResponseDto;
import com.dyinfotech.annualleavebackend.service.LeaveApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "관리자 전용 - 휴가 승인 관리", description = "조회/승인/반려 등 휴가 처리 API")
@RestController
@RequestMapping("/api/admin/leave-requests")
@RequiredArgsConstructor
public class LeaveApprovalController {

    private final LeaveApprovalService leaveApprovalService;

    @Operation(summary = "승인 대기 상태 휴가 조회", description = "관리자가 승인 대기 상태인 휴가를 page/size 기반으로 조회한다.")
    @GetMapping("/pending")
    public PageResponseDto<PendingLeaveRequestDto.PendingLeaveRequestResponse> getPendingRequests(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "cursorCreatedAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(value = "cursorRequestId", required = false) Long cursorRequestId,
            @AuthenticationPrincipal EmployeePrincipal principal) {
        return leaveApprovalService.getPendingRequests(
                principal.employeeId(), page, size, cursorCreatedAt, cursorRequestId);
    }

    @Operation(summary = "승인 상태 휴가 조회", description = "관리자가 하위팀의 승인 상태 휴가를 page/size 기반으로 조회한다.")
    @GetMapping("/approved")
    public PageResponseDto<LeaveRequestListDto.LeaveRequestListResponse> getApprovedRequests(
            @RequestParam(value = "team", required = false) String team,
            @RequestParam(value = "employeeParam", required = false) String employeeParam,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "cursorCreatedAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(value = "cursorRequestId", required = false) Long cursorRequestId,
            @AuthenticationPrincipal EmployeePrincipal principal) {
        return leaveApprovalService.getApprovedRequests(
                principal.employeeId(), team, employeeParam, page, size,
                cursorCreatedAt, cursorRequestId);
    }

    @Operation(summary = "반려 상태 휴가 조회", description = "관리자가 하위팀의 반려 상태 휴가를 page/size 기반으로 조회한다.")
    @GetMapping("/rejected")
    public PageResponseDto<LeaveRequestListDto.LeaveRequestListResponse> getRejectedRequests(
            @RequestParam(value = "team", required = false) String team,
            @RequestParam(value = "employeeParam", required = false) String employeeParam,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "cursorCreatedAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(value = "cursorRequestId", required = false) Long cursorRequestId,
            @AuthenticationPrincipal EmployeePrincipal principal) {
        return leaveApprovalService.getRejectedRequests(
                principal.employeeId(), team, employeeParam, page, size,
                cursorCreatedAt, cursorRequestId);
    }

    @Operation(summary = "휴가 승인", description = "관리자가 휴가 요청을 승인한다.")
    @ReplayAwareApproval
    @PostMapping("/{requestId}/approve")
    public LeaveApprovalDto.LeaveApprovalResponse approveLeaveRequest(@PathVariable("requestId") Long requestId, @AuthenticationPrincipal EmployeePrincipal principal) {
        return leaveApprovalService.approveLeaveRequest(requestId, principal.employeeId());
    }

    @Operation(summary = "휴가 반려", description = "관리자가 휴가 요청을 반려한다.")
    @ReplayAwareApproval
    @PostMapping("/{requestId}/reject")
    public LeaveRejectDto.LeaveRejectResponse rejectLeaveRequest(@PathVariable("requestId") Long requestId, @AuthenticationPrincipal EmployeePrincipal principal, @Valid @RequestBody LeaveRejectDto.LeaveRejectRequest request) {
        return leaveApprovalService.rejectLeaveRequest(requestId, principal.employeeId(), request);
    }
}
