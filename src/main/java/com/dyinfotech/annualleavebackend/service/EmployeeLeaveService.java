package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.common.type.BasisDataType;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.common.type.Sign;
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
     * 계산 규칙:
     * 1. 입사 1년 미만: 월차로 계산 (최대 11일)
     * 2. 입사 1년 이상: 기본 연차에서 근무 연수에 따라 추가 연차 부여
     *    - 기본 연차: basis_data seq=1 (FIRST_YEAR_LEAVE_DAYS)
     *    - 추가 기준: basis_data seq=2 (N_YEARS_OF_ADDITIONAL_LEAVE)
     *    - 추가 일수: basis_data seq=3 (ADDITIONAL_LEAVE_DAYS)
     *    - 예) N_YEARS=3이면 3년마다 ADDITIONAL_LEAVE_DAYS일 추가
     *    - 최대값: basis_data seq=6 (MAXIMUM_LEAVE_DAYS)
     * 
     * @param hireDate 연차를 계산할 직원의 입사일
     * @return calculatedLeaveDays
     */
    public float getCalculatedCurrYearLeaveDays(LocalDate hireDate) {
        LocalDate now = LocalDate.now(clock);
        
        float calculatedLeaveDays;
        // 1. 입사 1년 미만인지 판단
        if (now.isBefore(hireDate.plusYears(1))) {
            // 월차 계산: 입사월 제외, 이후 경과 월수
            int monthlyLeaveDays = calculateMonthlyLeaveDays(hireDate, now);
            calculatedLeaveDays = Math.min(monthlyLeaveDays, 11);
        } else {
            // 2. 근무 연수 계산
            int yearsOfService = Period.between(hireDate, now).getYears();

            // 3. BasisDataFactory에서 기초 데이터 조회
            float baseLeaveDay = basisDataFactory.getAsInteger(
                    BasisDataType.FIRST_YEAR_LEAVE_DAYS
            ).map(Integer::floatValue).orElseThrow(() -> 
                new IllegalArgumentException("기초 데이터에서 1년차 연차일수를 찾을 수 없습니다")
            );

            int nYearsOfAdditionalLeave = basisDataFactory.getAsInteger(
                    BasisDataType.N_YEARS_OF_ADDITIONAL_LEAVE
            ).orElseThrow(() -> 
                new IllegalArgumentException("기초 데이터에서 추가연차 기준 연수를 찾을 수 없습니다")
            );

            int additionalLeaveDays = basisDataFactory.getAsInteger(
                    BasisDataType.ADDITIONAL_LEAVE_DAYS
            ).orElseThrow(() -> 
                new IllegalArgumentException("기초 데이터에서 추가연차 일수를 찾을 수 없습니다")
            );

            float maximumLeaveDays = basisDataFactory.getAsInteger(
                    BasisDataType.MAXIMUM_LEAVE_DAYS
            ).map(Integer::floatValue).orElseThrow(() -> 
                new IllegalArgumentException("기초 데이터에서 최대 연차일수를 찾을 수 없습니다")
            );

            // 4. 추가 연차 계산
            // 근무 연수에서 1을 뺀 값이 nYearsOfAdditionalLeave 이상이면 추가 연차 부여
            // 예) nYearsOfAdditionalLeave=3, yearsOfService=5 → additionalYears=4 → (4/3=1일) * additionalLeaveDays 추가
            int additionalYears = yearsOfService - 1;
            if (additionalYears >= nYearsOfAdditionalLeave) {
                baseLeaveDay += (additionalYears / nYearsOfAdditionalLeave) * additionalLeaveDays;
            }

            // 5. 최대값 초과 방지
            calculatedLeaveDays = Math.min(baseLeaveDay, maximumLeaveDays);
        }

        // 6. 계산된 값 반환
        return calculatedLeaveDays;
    }
    public float getCalculatedCurrYearLeaveDays(Employee employee) {
    	return getCalculatedCurrYearLeaveDays(employee.getHireDate());
    }

    /**
     * 입사일 기준으로 경과 월수를 계산한다. (입사월은 제외)
     * 
     * 예) 입사: 2026-07-01, 현재: 2026-10-15
     *    → 2026-08-01(1개월), 2026-09-01(2개월), 2026-10-01(3개월) = 3개월
     * 
     * @param hireDate 입사일
     * @param now 현재 날짜
     * @return 경과 개월 수
     */
    private int calculateMonthlyLeaveDays(LocalDate hireDate, LocalDate now) {
        int count = 0;

        for (int i = 1; i < 12; i++) {
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
    
    public Role resolveRole(Long employeeId) {
        return teamRepository.existsByProjectManager_EmployeeId(employeeId) ? Role.ADMIN : Role.EMPLOYEE;
    }
    public float getAdjustedLeaveDays(Long employeeId, String year) {
        return leaveAdjustmentRepository.sumAdjustedLeaveDays(employeeId, year, Sign.PLUS.getName());
    }
}
