package com.dyinfotech.annualleavebackend.repository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.repository.projection.LeaveRequestStatusCount;
import com.dyinfotech.annualleavebackend.repository.query.LeaveRequestRepositoryCustom;

import io.jsonwebtoken.lang.Collections;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long>, LeaveRequestRepositoryCustom {
	// 올해의 연도는 Year.now(Clock)를 파라미터로 넘긴다.
	private static LocalDate getStartOfYear(Year year) {
		return year.atDay(1);
	}
	// 올해의 연도는 Year.now(Clock)를 파라미터로 넘긴다.
    private static LocalDate getEndOfYear(Year year) {
        return year.atMonth(Month.DECEMBER).atEndOfMonth();
    }
    

    // 승인된 요청의 사용일수 합계 (잔여 연차 계산용)
    default float sumRequestedUseDays(Long employeeId, Year year) {
    	return sumRequestedUseDays(employeeId, List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING), getStartOfYear(year), getEndOfYear(year));
    }
    default float sumRequestedUseDays(Long employeeId, Clock clock) {
    	return sumRequestedUseDays(employeeId, Year.now(clock));
    }
    default Map<Long, Float> sumRequestedUseDays(Collection<Long> employeeIds, Year year) {
    	return sumRequestedUseDays(employeeIds, List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING), getStartOfYear(year), getEndOfYear(year));
    }
    default Map<Long, Float> sumRequestedUseDays(Collection<Long> employeeIds, Clock clock) {
    	return sumRequestedUseDays(employeeIds, Year.now(clock));
    }

    // 특정 상태의 내 요청 개수
    default List<LeaveRequestStatusCount> countByStatus(Long employeeId, Year year) {
    	return countByStatus(employeeId, getStartOfYear(year), getEndOfYear(year));
    }
    default List<LeaveRequestStatusCount> countByStatus(Long employeeId, Clock clock) {
    	return countByStatus(employeeId, Year.now(clock));
    }

    // 전직원 기준 특정 상태 요청 개수 (관리자용)
	default List<LeaveRequestStatusCount> countByStatus(Long excludeId, Collection<String> directTeams, Collection<String> accessibleTeams, Collection<Long> childTeamProjectManagerIds, Year year) {
		if (directTeams == null || directTeams.isEmpty() || accessibleTeams == null || accessibleTeams.isEmpty()) {
			return Collections.emptyList();
		}
		return countByStatus(excludeId, directTeams, accessibleTeams, childTeamProjectManagerIds, getStartOfYear(year), getEndOfYear(year));
	}
	default List<LeaveRequestStatusCount> countByStatus(Long excludeId, Collection<String> directTeams, Collection<String> accessibleTeams, Collection<Long> childTeamProjectManagerIds, Clock clock) {
		return countByStatus(excludeId, directTeams, accessibleTeams, childTeamProjectManagerIds, Year.now(clock));
	}

	// 승인 대기 상태 휴가 조회 (관리자용)
    default List<LeaveRequest> findByStatusOrderByCreatedAtAsc(Long excludeId, Collection<String> directTeams, Collection<Long> childTeamProjectManagerIds, LeaveRequestStatus status, Year year) {
    	if (directTeams == null || directTeams.isEmpty()) {
    		return Collections.emptyList();
    	}
    	return findByStatusAndTeamsInRange(excludeId, status, directTeams, childTeamProjectManagerIds, getStartOfYear(year), getEndOfYear(year));
    }
    default List<LeaveRequest> findByStatusOrderByCreatedAtAsc(Long excludeId, Collection<String> directTeams, Collection<Long> childTeamProjectManagerIds, LeaveRequestStatus status, Clock clock) {
    	return findByStatusOrderByCreatedAtAsc(excludeId, directTeams, childTeamProjectManagerIds, status, Year.now(clock));
    }

    @EntityGraph(attributePaths = {"employee", "manager"})
    Optional<LeaveRequest> findByRequestId(Long requestId);

    Optional<LeaveRequest> findByEmployee_EmployeeIdAndCreateRequestKey(
            Long employeeId,
            String createRequestKey);

    default Optional<LeaveRequest> findDetailById(Long requestId) {
        return findByRequestId(requestId);
    }

    List<LeaveRequest> findByEmployeeEmployeeIdAndStatusInAndStartDateBetween(
            Long employeeId,
            Collection<LeaveRequestStatus> status,
            LocalDate yearStart,
            LocalDate yearEnd
    );

    default List<LeaveRequest> findActiveLeaveRequests(Long employeeId, LocalDate yearStart, LocalDate yearEnd) {
        return findByEmployeeEmployeeIdAndStatusInAndStartDateBetween(
                employeeId,
                List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING),
                yearStart,
                yearEnd
        );
    }

}