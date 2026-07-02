package com.dyinfotech.annualleavebackend.controller;

import com.dyinfotech.annualleavebackend.dto.LeaveApprovalDto;
import com.dyinfotech.annualleavebackend.dto.PendingLeaveRequestDto;
import com.dyinfotech.annualleavebackend.service.LeaveApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/leave-requests")
@RequiredArgsConstructor
public class LeaveApprovalController {

    private final LeaveApprovalService leaveApprovalService;

    @GetMapping("/pending")
    public List<PendingLeaveRequestDto.PendingLeaveRequestResponse> getPendingRequests() {
        return leaveApprovalService.getPendingRequests();
    }

    @PostMapping("/{requestId}/approve")
    public LeaveApprovalDto.LeaveApprovalResponse approve(@PathVariable Long requestId, @AuthenticationPrincipal Long approverId) {
        return leaveApprovalService.approve(requestId, approverId);
    }
}
