package com.dyinfotech.annualleavebackend.controller;

import com.dyinfotech.annualleavebackend.dto.PendingLeaveRequestDto;
import com.dyinfotech.annualleavebackend.service.LeaveApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
