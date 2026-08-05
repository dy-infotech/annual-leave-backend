package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.Year;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.common.type.BasisDataType;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.common.type.Sign;
import com.dyinfotech.annualleavebackend.config.CommonConfig;
import com.dyinfotech.annualleavebackend.domain.BasisData;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveAdjustmentRepository;
import com.dyinfotech.annualleavebackend.repository.TeamRepository;

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

    private final BasisDataFactory basisDataFactory;
    private final LeaveAdjustmentRepository leaveAdjustmentRepository;
    private final TeamRepository teamRepository;
    // XXX: EmployeeService가 EmployeeLeaveService를 참조하고 있다. 상호 참조 이슈를 방지하기 위해 EmployeeRepository를 사용하도록 허용한다
    private final EmployeeRepository employeeRepository;
    
    private final Clock clock;
    
    /**
     * 전직원 새해 연차 롤오버 및 재계산
     * 이 메서드가 끝나는 순간 전직원 변경사항이 DB에 Commit 되며 락(Lock)이 즉시 해제
     * @param currentYear
     */
    @Transactional
    public void renewAllActiveEmployeesLeave(String currentYear) {
        // 1. 퇴사자를 제외한 전직원 목록 조회 (필요 시 패치 조인이나 벌크 연산 고려)
        List<Employee> activeEmployees = employeeRepository.findAllByFireDateIsNull();
        
        // 2. 루프를 돌며 안전하게 연차 갱신
        for (Employee employee : activeEmployees) {
            try {
            	String prevYear = employee.getCurrYear();
            	if (prevYear != null && !prevYear.equals(currentYear)) {
            		employee.setPrevYear(prevYear);
            		employee.setPrevYearLeaveDays(employee.getCurrTotalLeaveDays());
            		employee.setCurrYear(currentYear);
            		employee.setCurrYearLeaveDays(getCalculatedCurrYearLeaveDays(employee));
                    log.info("직원 번호 [{}] 연차 갱신 완료", employee.getEmployeeNumber());
            	} else {
            		log.error("직원 번호 [{}] 연차 갱신 실패", employee.getEmployeeNumber());
            	}
            } catch (Exception e) {
                // 한 명이 에러 나도 다른 직원들은 갱신되어야 하므로 예외 처리 개별 적용
                log.error("직원 번호 [{}] 연차 갱신 중 에러 발생: {}", employee.getEmployeeNumber(), e.getMessage());
            }
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
    	// 입사 1년 미만 여부를 판단하는 기준 날짜
    	LocalDate nextYearDateFromHireDate = hireDate.plusYears(1);
    	// 근속연수 계산 기준 날짜
        LocalDate serviceStartDate = hireDate;
		if (basisDataFactory.getAsBoolean(BasisDataType.USE_FISCAL_YEAR_LEAVE_POLICY)
		        			.orElse(CommonConfig.USE_FISCAL_YEAR_LEAVE_POLICY)) {
    		// 회계연도 정책:
    		// - 입사 다음 해 1월 1일부터 연차 부여
    		// - 근속연수는 입사연도 1월 1일 기준으로 계산
    		nextYearDateFromHireDate = Year.of(hireDate.getYear() + 1).atDay(1);
        	serviceStartDate = Year.of(hireDate.getYear()).atDay(1);
    	}
    	
        // 입사 1년 미만 근로자
        if (now.isBefore(nextYearDateFromHireDate)) {
            return Math.min(calculateMonthlyLeaveCount(hireDate, now), MAX_FIRST_YEAR_MONTHLY_LEAVE_COUNT);
        }

        // 입사 1년 이상 근로자
        int yearsOfService = Period.between(serviceStartDate, now).getYears();
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

    public interface SingleEmployeeRoleResolver {
    	boolean isAdmin();
    	default Role resolveRole() {
    		return EmployeeLeaveService.convertRole(isAdmin());
    	}
    }
	public interface MultipleEmployeeRoleResolver {
		boolean isAdmin(Long employeeId);
		default Role resolveRole(Long employeeId) {
			return EmployeeLeaveService.convertRole(isAdmin(employeeId));
		}
	}
	@RequiredArgsConstructor
	private static class SingleEmployeeRoleResolverImpl implements SingleEmployeeRoleResolver {
		private final boolean admin;

		@Override
		public boolean isAdmin() {
			return admin;
		}
	}
	@RequiredArgsConstructor
	private static class MultipleEmployeeRoleResolverImpl implements MultipleEmployeeRoleResolver {
		private final Set<Long> projectManagerIds;

		@Override
		public boolean isAdmin(Long employeeId) {
			return projectManagerIds.contains(employeeId);
		}
	}
	public MultipleEmployeeRoleResolver createRoleResolver() {
		return new MultipleEmployeeRoleResolverImpl(Set.copyOf(teamRepository.findAllProjectManagerIds()));
	}
	public MultipleEmployeeRoleResolver createRoleResolver(Collection<Long> targetEmployeeIds) {
		return new MultipleEmployeeRoleResolverImpl(Set.copyOf(teamRepository.findAllProjectManagerIdsByEmployeeIds(targetEmployeeIds)));
	}
	public SingleEmployeeRoleResolver createSingleRoleResolver(Role role) {
		return new SingleEmployeeRoleResolverImpl(Role.isAdmin(role));
	}
	public SingleEmployeeRoleResolver createSingleRoleResolver(Long employeeId) {
		return new SingleEmployeeRoleResolverImpl(teamRepository.existsByProjectManager_EmployeeId(employeeId));
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
