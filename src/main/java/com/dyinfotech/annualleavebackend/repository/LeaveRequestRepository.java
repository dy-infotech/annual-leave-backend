package com.dyinfotech.annualleavebackend.repository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.domain.Team;
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
    default float sumApprovedUseDays(Long employeeId, Year year) {
    	return sumApprovedUseDays(employeeId, List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING), getStartOfYear(year), getEndOfYear(year));
    }
    default float sumApprovedUseDays(Long employeeId, Clock clock) {
    	return sumApprovedUseDays(employeeId, Year.now(clock));
    }
    default Map<Long, Float> sumApprovedUseDays(Collection<Long> employeeIds, Year year) {
    	return sumApprovedUseDays(employeeIds, List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING), getStartOfYear(year), getEndOfYear(year));
    }
    default Map<Long, Float> sumApprovedUseDays(Collection<Long> employeeIds, Clock clock) {
    	return sumApprovedUseDays(employeeIds, Year.now(clock));
    }

    // 특정 상태의 내 요청 개수
    default List<LeaveRequestStatusCount> countByStatus(Long employeeId, Year year) {
    	return countByStatus(employeeId, getEndOfYear(year), getStartOfYear(year));
    }
    default List<LeaveRequestStatusCount> countByStatus(Long employeeId, Clock clock) {
    	return countByStatus(employeeId, Year.now(clock));
    }

    // 전직원 기준 특정 상태 요청 개수 (관리자용)
	default List<LeaveRequestStatusCount> countByStatus(Collection<String> directTeams, Collection<String> accessibleTeams, Year year) {
		if (directTeams == null || directTeams.isEmpty() || accessibleTeams == null || accessibleTeams.isEmpty()) {
			return Collections.emptyList();
		}
		return countByStatus(directTeams, accessibleTeams, getEndOfYear(year), getStartOfYear(year));
	}
	default List<LeaveRequestStatusCount> countByStatus(Collection<String> directTeams, Collection<String> accessibleTeams, Clock clock) {
		return countByStatus(directTeams, accessibleTeams, Year.now(clock));
	}
	
	// 승인 대기 상태 휴가 조회 (관리자용)
    default List<LeaveRequest> findByStatusOrderByCreatedAtAsc(List<Team> teams, LeaveRequestStatus status, Year year) {
    	if (teams == null || teams.isEmpty())	return Collections.emptyList();
    	return findByStatusAndTeamsInRange(status, teams.stream().map(Team::getTeam).toList(), getEndOfYear(year), getStartOfYear(year));
    }
    default List<LeaveRequest> findByStatusOrderByCreatedAtAsc(List<Team> teams, LeaveRequestStatus status, Clock clock) {
    	return findByStatusOrderByCreatedAtAsc(teams, status, Year.now(clock));
    }
}