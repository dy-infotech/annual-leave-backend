package com.dyinfotech.annualleavebackend.controller;

import com.dyinfotech.annualleavebackend.dto.LeaveRequestDto;
import com.dyinfotech.annualleavebackend.service.LeaveRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestDto.LeaveRequestCreateResponse createLeaveRequest(@AuthenticationPrincipal Long employeeId, @Valid @RequestBody LeaveRequestDto.LeaveRequestCreateRequest request) {
        return leaveRequestService.createLeaveRequest(employeeId, request);
    }
}