package com.dyinfotech.annualleavebackend.controller;

import java.time.Clock;
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

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestDetailDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestDto;
import com.dyinfotech.annualleavebackend.dto.LeaveRequestListDto;
import com.dyinfotech.annualleavebackend.dto.SpecialDayDto;
import com.dyinfotech.annualleavebackend.service.LeaveRequestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "휴가 신청 관리", description = "휴가 신청/취소/조회, 휴가 신청 시 참고할 공휴일 정보 조회 API")
@RestController
@RequestMapping("/api/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final Clock clock;

    @Operation(summary = "금년 공휴일 조회", description = "휴가 신청 화면의 캘린더에 표시할 금년 공휴일 정보를 조회한다.")
    @GetMapping("/current-year-special-days")
    public List<SpecialDayDto.SpecialDayResponse> getCurrentYearSpecialDays() {
    	return leaveRequestService.getHolidays(LocalDate.now(clock).getYear());
    }

    @Operation(summary = "차년도 공휴일 조회", description = "휴가 신청 화면의 캘린더에 표시할 차년도 공휴일 정보를 조회한다.")
    @GetMapping("/next-year-special-days")
    public List<SpecialDayDto.SpecialDayResponse> getNextYearSpecialDays() {
    	return leaveRequestService.getHolidays(LocalDate.now(clock).getYear() + 1);
    }

    @Operation(summary = "휴가 신청", description = "휴가를 신청한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestDto.LeaveRequestCreateResponse createLeaveRequest(@AuthenticationPrincipal EmployeePrincipal principal, @Valid @RequestBody LeaveRequestDto.LeaveRequestCreateRequest request) {
        return leaveRequestService.createLeaveRequest(principal.employeeId(), request);
    }

    @Operation(summary = "전체 휴가 신청 정보 조회", description = "검색 조건과 일치하는 휴가 신청 정보를 조회한다.")
    @GetMapping("/all")
    public List<LeaveRequestListDto.LeaveRequestListResponse> searchLeaveRequests(
    		@ModelAttribute LeaveRequestListDto.LeaveRequestListRequest condition
    ) {
        return leaveRequestService.searchLeaveRequests(condition);
    }

//    @Operation(summary = "휴가 신청 상세 조회", description = "특정 휴가 신청의 상세 정보를 조회한다. 사유는 본인 또는 관리자만 조회 가능하다.")
//    @GetMapping("/{requestId}")
//    public LeaveRequestDetailDto.LeaveRequestDetailResponse getLeaveRequestDetail(
//            @PathVariable Long requestId,
//            @AuthenticationPrincipal EmployeePrincipal principal,
//            Authentication authentication) {
 // ⚙️ 수정 후 (괄호 안에 파라미터 이름을 명시적으로 추가)
    @Operation(summary = "휴가 신청 상세 조회", description = "특정 휴가 신청의 상세 정보를 조회한다. 사유는 본인 또는 관리자만 조회 가능하다.")
    @GetMapping("/{requestId}")
    public LeaveRequestDetailDto.LeaveRequestDetailResponse getLeaveRequestDetail(
            @PathVariable("requestId") Long requestId, // 👈 ("requestId") 이름을 명시하여 URL 매핑 문제를 해결합니다.
            @AuthenticationPrincipal EmployeePrincipal principal) {
        return leaveRequestService.getLeaveRequestDetail(requestId, principal.employeeId(), Role.isAdmin(principal.role()));
    }

    @Operation(summary = "내 휴가 신청 정보 조회", description = "검색 조건과 일치하는 내 휴가 신청 정보를 조회한다.")
    @GetMapping("/my")
    public List<LeaveRequestListDto.LeaveRequestListResponse> searchMyLeaveRequests(
            @AuthenticationPrincipal EmployeePrincipal principal,
    		@ModelAttribute LeaveRequestListDto.LeaveRequestListRequest condition
    ) {
    	condition.setEmployeeId(principal.employeeId());
        return leaveRequestService.searchLeaveRequests(condition);
    }

    @Operation(summary = "휴가 신청 취소", description = "내가 신청한 휴가를 취소한다.")
    @DeleteMapping("/{requestId}")
    public ResponseEntity<Void> cancelLeaveRequest(@AuthenticationPrincipal EmployeePrincipal principal, @PathVariable("requestId") Long requestId) {
        leaveRequestService.cancel(principal.employeeId(), requestId);
        return ResponseEntity.noContent().build();
    }
}