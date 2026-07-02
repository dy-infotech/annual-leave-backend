package com.dyinfotech.annualleavebackend.service;

import com.dyinfotech.annualleavebackend.domain.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.dto.PendingLeaveRequestDto;
import com.dyinfotech.annualleavebackend.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveApprovalService {

    private final LeaveRequestRepository leaveRequestRepository;

    public List<PendingLeaveRequestDto.PendingLeaveRequestResponse> getPendingRequests() {
        return leaveRequestRepository.findByStatusOrderByCreatedAtAsc(LeaveRequestStatus.PENDING)
                .stream()
                .map(PendingLeaveRequestDto.PendingLeaveRequestResponse::from)
                .toList();
    }
}