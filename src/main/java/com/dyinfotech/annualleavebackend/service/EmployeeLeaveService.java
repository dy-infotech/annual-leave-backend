package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.Period;
import java.util.ArrayList;
import java.time.Year;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.common.type.BasisDataType;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.common.type.Sign;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveAdjustmentRepository;

import com.dyinfotech.annualleavebackend.service.TeamService.ManagedTeam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 직원의 연차 계산을 담당하는 서비스.
 * BasisDataFactory에서 기초 데이터(연차 기준)를 조회하여 현재 연도 연차일수를 계산한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeLeaveService {

    private static final int ROLLOVER_BATCH_SIZE = 200;

    private final BasisDataFactory basisDataFactory;
    private final LeaveAdjustmentRepository leaveAdjustmentRepository;
    private final TeamService teamService;
    // XXX: EmployeeService가 EmployeeLeaveService를 참조하고 있다. 상호 참조 이슈를 방지하기 위해 EmployeeRepository를 사용하도록 허용한다
    private final EmployeeRepository employeeRepository;
    private final EmployeeCacheInvalidator employeeCacheInvalidator;
    private final PlatformTransactionManager transactionManager;
    
    private final Clock clock;
    
    /**
     * 전직원 새해 연차 롤오버 및 재계산.
     * employee_id keyset으로 대상을 나누고 각 batch를 독립 transaction으로 commit해
     * 장시간 transaction/lock 보유와 stale overwrite 범위를 제한한다.
     *
     * @param currentYear 갱신할 현재 연도
     */
    public void renewAllActiveEmployeesLeave(String currentYear) {
        LocalDate today = LocalDate.now(clock);
        Long afterEmployeeId = null;
        List<Long> failedEmployeeIds = new ArrayList<>();

        TransactionTemplate batchTransaction = new TransactionTemplate(transactionManager);
        batchTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        while (true) {
            List<Long> employeeIds = employeeRepository.findActiveEmployeeIdsAfter(
                    today,
                    afterEmployeeId,
                    ROLLOVER_BATCH_SIZE);
            if (employeeIds.isEmpty()) {
                break;
            }

            List<Long> batchIds = List.copyOf(employeeIds);
            try {
                batchTransaction.executeWithoutResult(
                        status -> renewActiveEmployeeBatch(batchIds, currentYear, today));
            } catch (RuntimeException batchError) {
                log.error(
                        "연차 롤오버 batch transaction 실패. 개별 transaction으로 재시도합니다. employeeIds={}",
                        batchIds,
                        batchError);
                failedEmployeeIds.addAll(
                        retryRolloverIndividually(batchIds, currentYear, today));
            }

            afterEmployeeId = employeeIds.get(employeeIds.size() - 1);
        }

        if (!failedEmployeeIds.isEmpty()) {
            throw new IllegalStateException(
                    "일부 직원의 연차 롤오버에 실패했습니다. employeeIds="
                            + failedEmployeeIds);
        }
    }

    private List<Long> retryRolloverIndividually(
            List<Long> employeeIds,
            String currentYear,
            LocalDate today) {
        TransactionTemplate employeeTransaction = new TransactionTemplate(transactionManager);
        employeeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<Long> failedEmployeeIds = new ArrayList<>();

        for (Long employeeId : employeeIds) {
            try {
                employeeTransaction.executeWithoutResult(
                        status -> renewActiveEmployeeBatch(
                                List.of(employeeId),
                                currentYear,
                                today));
            } catch (RuntimeException employeeError) {
                failedEmployeeIds.add(employeeId);
                log.error(
                        "직원 연차 롤오버 개별 transaction 실패. employeeId={}",
                        employeeId,
                        employeeError);
            }
        }
        return failedEmployeeIds;
    }

    private void renewActiveEmployeeBatch(
            List<Long> employeeIds,
            String currentYear,
            LocalDate today) {
        List<Employee> activeEmployees =
                employeeRepository.findAllByIdsForUpdate(employeeIds);
        List<Long> renewedEmployeeIds = new ArrayList<>();

        for (Employee employee : activeEmployees) {
            if (!employee.isActive(today)) {
                continue;
            }

            String prevYear = employee.getCurrYear();
            if (prevYear != null && !prevYear.equals(currentYear)) {
                // 계산 단계에서 오류가 나면 entity를 수정하기 전에 transaction 전체를
                // rollback하여 부분 롤오버가 flush되지 않게 한다.
                float nextLeaveDays = getCalculatedCurrYearLeaveDays(employee);
                float previousTotalLeaveDays = employee.getCurrTotalLeaveDays();

                employee.setPrevYear(prevYear);
                employee.setPrevYearLeaveDays(previousTotalLeaveDays);
                employee.setCurrYear(currentYear);
                employee.setCurrYearLeaveDays(nextLeaveDays);
                renewedEmployeeIds.add(employee.getEmployeeId());
                log.info("직원 번호 [{}] 연차 갱신 완료", employee.getEmployeeNumber());
            } else {
                log.info("직원 번호 [{}] 연차 갱신 불필요", employee.getEmployeeNumber());
            }
        }

        if (!renewedEmployeeIds.isEmpty()) {
            employeeCacheInvalidator.afterEmployeeViewChange(renewedEmployeeIds);
        }
    }
    
    /**
     * 직원의 현재 연도 연차일수를 계산해 반환한다
     * 
     * 근로기준법 기준:
     * 1. 입사 1년 미만:
     *    - 1개월 개근 시 1일 발생
     *    - 최대 11일
     *
     * 2. 입사 1년 이상:
     *    - 기본 15일
     *    - 3년 이상부터 매 2년마다 1일 가산
     *    - 최대 25일
     *    
     *    - 기본 연차: basis_data seq=1 (FIRST_YEAR_LEAVE_DAYS)
     *    - 추가 기준: basis_data seq=2 (YEARS_PER_ADDITIONAL_LEAVE)
     *    - 추가 일수: basis_data seq=3 (ADDITIONAL_LEAVE_DAYS)
     *    - 예) YEARS_PER_ADDITIONAL_LEAVE=2이면 3년차부터 2년마다 1일씩 추가
     *    - 최대값: basis_data seq=6 (MAXIMUM_LEAVE_DAYS)
     * 
     * @param hireDate 연차를 계산할 직원의 입사일
     * @param now 연차를 계산할 기준일 (보통 현재 날짜)
     * @return calculatedLeaveDays 발생 연차 일수
     */
    private static final int MAX_FIRST_YEAR_MONTHLY_LEAVE_COUNT = 11;	// 입사 1년 미만 근로자는 매월 개근 시 1일 발생하며 최대 11일
    public float getCalculatedCurrYearLeaveDays(LocalDate hireDate, LocalDate now) {
        if (hireDate.isAfter(now)) {
            return 0.0f;
        }

        // 당해년도 입사자는 현재까지 발생한 월차를 계산
        if (hireDate.getYear() == now.getYear()) {
            return Math.min(calculateMonthlyLeaveCount(hireDate, now), MAX_FIRST_YEAR_MONTHLY_LEAVE_COUNT);
        }

        // 이전 연도 입사자는 현재 회계연도 말일 기준으로 근속연수를 계산
        Year currentYear = Year.from(now);
        LocalDate yearEnd = currentYear.atMonth(Month.DECEMBER).atEndOfMonth();
        int yearsOfService = Period.between(hireDate, yearEnd).getYears();
        int baseLeaveDays = basisDataFactory.getAsInteger(BasisDataType.FIRST_YEAR_LEAVE_DAYS)
							                .orElseThrow(() -> new IllegalArgumentException("기본 연차 일수를 찾을 수 없습니다"));

        int yearsPerAdditionalLeave = basisDataFactory.getAsInteger(BasisDataType.YEARS_PER_ADDITIONAL_LEAVE)
								                .orElseThrow(() -> new IllegalArgumentException("가산연차 주기를 찾을 수 없습니다"));

        int additionalLeaveDays = basisDataFactory.getAsInteger(BasisDataType.ADDITIONAL_LEAVE_DAYS)
								                .orElseThrow(() -> new IllegalArgumentException("가산연차 일수를 찾을 수 없습니다"));


        int maximumLeaveDays = basisDataFactory.getAsInteger(BasisDataType.MAXIMUM_LEAVE_DAYS)
							                .orElseThrow(() -> new IllegalArgumentException("최대 연차 일수를 찾을 수 없습니다"));
        /*
         * 가산 연차 계산
         *
         * 근로기준법:
         * - 근속 3년 이상부터 매 2년마다 1일씩 가산
         *
         * 계산식:
         * (근속연수 - 1) / 가산주기
         *
         * 예)
         * 1년 → (1-1)/2 = 0
         * 2년 → (2-1)/2 = 0
         * 3년 → (3-1)/2 = 1
         * 4년 → (4-1)/2 = 1
         * 5년 → (5-1)/2 = 2
         */
        int additionalLeaveCount = (yearsOfService - 1) / yearsPerAdditionalLeave;
        // 기본 연차 + 가산 연차를 계산하되 법정 최대 연차(25일)를 초과하지 않도록 제한
        int calculatedLeaveDays = baseLeaveDays + additionalLeaveCount * additionalLeaveDays;
        return Math.min(calculatedLeaveDays, maximumLeaveDays);
    }
    public float getCalculatedCurrYearLeaveDays(LocalDate hireDate) {
        return getCalculatedCurrYearLeaveDays(hireDate, LocalDate.now(clock));
    }
    public float getCalculatedCurrYearLeaveDays(Employee employee) {
    	return getCalculatedCurrYearLeaveDays(employee.getHireDate());
    }
    
    /**
     * 입사일 기준으로 1개월 단위 경과 횟수를 계산한다.
     *
     * 입사 후 1개월이 경과한 시점부터 월차 발생 대상으로 계산한다.
     *
     * 예) 입사: 2026-07-01, 현재: 2026-10-15
     *     → 2026-08-01, 2026-09-01, 2026-10-01
     *     = 3개월 경과
     * @param hireDate 입사일
     * @param now 현재 날짜
     * @return 경과 개월 수
     */
    private int calculateMonthlyLeaveCount(LocalDate hireDate, LocalDate now) {
    	int count = 0;
    	
    	for (int i = 1; i <= MAX_FIRST_YEAR_MONTHLY_LEAVE_COUNT; i++) {
    		LocalDate occurrenceDate = hireDate.plusMonths(i);
    		
    		// 현재 날짜를 넘지 않으면 카운트 증가
    		if (!occurrenceDate.isAfter(now)) {
    			count++;
    		} else {
    			break;
    		}
    	}
    	
    	return count;
    }

	public interface EmployeeAuthorityResolver {
        boolean isAdmin(Long employeeId);
        Collection<ManagedTeam> getManagedTeams(Long employeeId);

        default Role resolveRole(Long employeeId) {
            return EmployeeLeaveService.convertRole(isAdmin(employeeId));
        }
    }

    private static class EmployeeAuthorityResolverImpl implements EmployeeAuthorityResolver {
        private final Map<Long, Set<ManagedTeam>> managedTeams;

        public EmployeeAuthorityResolverImpl(Collection<ManagedTeam> teams) {
            this.managedTeams = teams.stream()
                    .collect(Collectors.groupingBy(ManagedTeam::projectManagerId, Collectors.toSet()));
        }

        @Override
        public Collection<ManagedTeam> getManagedTeams(Long employeeId) {
            return managedTeams.getOrDefault(employeeId, Collections.emptySet());
        }

        @Override
        public boolean isAdmin(Long employeeId) {
            return managedTeams.containsKey(employeeId);
        }
    }

    public EmployeeAuthorityResolver createAuthorityResolver() {
        return new EmployeeAuthorityResolverImpl(teamService.findAll());
    }

    public EmployeeAuthorityResolver createAuthorityResolver(Set<Long> targetEmployeeIds) {
        return new EmployeeAuthorityResolverImpl(
                teamService.findAll().stream()
                        .filter(team -> targetEmployeeIds.contains(team.projectManagerId()))
                        .collect(Collectors.toSet()));
    }

    public EmployeeAuthorityResolver createAuthorityResolver(Long employeeId) {
        return createAuthorityResolver(Set.of(employeeId));
    }

    private static Role convertRole(boolean isAdmin) {
    	return isAdmin ? Role.getAdminRole() : Role.EMPLOYEE;
    }
    
    public float getAdjustedLeaveDays(Long employeeId, String year) {
        return leaveAdjustmentRepository.sumAdjustedLeaveDays(employeeId, year, Sign.PLUS.getName());
    }
    public Map<Long, Float> getAdjustedLeaveDays(Collection<Long> employeeIds, String year) {
        return leaveAdjustmentRepository.sumAdjustedLeaveDays(employeeIds, year, Sign.PLUS.getName());
    }
}