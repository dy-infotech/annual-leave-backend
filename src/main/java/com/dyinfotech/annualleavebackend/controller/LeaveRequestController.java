package com.dyinfotech.annualleavebackend.controller;

import com.dyinfotech.annualleavebackend.domain.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestListDto;
import com.dyinfotech.annualleavebackend.service.LeaveRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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

    @GetMapping("/all")
    public List<LeaveRequestListDto.LeaveRequestListResponse> searchLeaveRequests(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) LeaveRequestStatus status
    ) {
        LeaveRequestListDto.LeaveRequestListRequest condition = new LeaveRequestListDto.LeaveRequestListRequest(employeeId, startDate, endDate, status);
        return leaveRequestService.searchLeaveRequests(condition);
    }

    // 내 목록: employeeId를 로그인한 사용자 ID로 고정
    @GetMapping("/my")
    public List<LeaveRequestListDto.LeaveRequestListResponse> searchMyLeaveRequests(
            @AuthenticationPrincipal Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) LeaveRequestStatus status
    ) {
        LeaveRequestListDto.LeaveRequestListRequest condition = new LeaveRequestListDto.LeaveRequestListRequest(employeeId, startDate, endDate, status);
        return leaveRequestService.searchLeaveRequests(condition);
    }
}