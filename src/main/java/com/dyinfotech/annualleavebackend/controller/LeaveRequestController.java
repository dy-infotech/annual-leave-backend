package com.dyinfotech.annualleavebackend.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dyinfotech.annualleavebackend.dto.LeaveRequestDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestListDto;
import com.dyinfotech.annualleavebackend.dto.SpecialDayDto;
import com.dyinfotech.annualleavebackend.service.LeaveRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    
    @GetMapping("/current-year-special-days")
    public List<SpecialDayDto.SpecialDayResponse> getCurrentYearSpecialDays() {
    	return leaveRequestService.getHolidays(String.valueOf(LocalDate.now().getYear()));
    }
    
    @GetMapping("/next-year-special-days")
    public List<SpecialDayDto.SpecialDayResponse> getNextYearSpecialDays() {
    	return leaveRequestService.getHolidays(String.valueOf(LocalDate.now().getYear() + 1));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestDto.LeaveRequestCreateResponse createLeaveRequest(@AuthenticationPrincipal Long employeeId, @Valid @RequestBody LeaveRequestDto.LeaveRequestCreateRequest request) {
        return leaveRequestService.createLeaveRequest(employeeId, request);
    }

    @GetMapping("/all")
    public List<LeaveRequestListDto.LeaveRequestListResponse> searchLeaveRequests(
    		@ModelAttribute LeaveRequestListDto.LeaveRequestListRequest condition
    ) {
        return leaveRequestService.searchLeaveRequests(condition);
    }

    // 내 휴가 신청 목록 조회는 employeeId를 로그인한 사용자 ID로 고정
    @GetMapping("/my")
    public List<LeaveRequestListDto.LeaveRequestListResponse> searchMyLeaveRequests(
            @AuthenticationPrincipal Long employeeId,
    		@ModelAttribute LeaveRequestListDto.LeaveRequestListRequest condition
    ) {
    	condition.setEmployeeId(employeeId);
        return leaveRequestService.searchLeaveRequests(condition);
    }

    @DeleteMapping("/{requestId}")
    public ResponseEntity<Void> cancelLeaveRequest(@AuthenticationPrincipal Long employeeId, @PathVariable Long requestId) {
        leaveRequestService.cancel(employeeId, requestId);
        return ResponseEntity.noContent().build();
    }
}