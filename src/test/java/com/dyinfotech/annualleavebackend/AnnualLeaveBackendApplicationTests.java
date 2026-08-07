package com.dyinfotech.annualleavebackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.EnableCaching;

import com.dyinfotech.annualleavebackend.config.CommonConfig;
import com.dyinfotech.annualleavebackend.service.EmployeeLeaveService;
import com.dyinfotech.annualleavebackend.service.EmployeeService;

import jakarta.persistence.EntityManagerFactory;

@SpringBootTest
@EnableCaching
class AnnualLeaveBackendApplicationTests {
	
	@Autowired
	private EmployeeService employeeService;
	
	@Autowired
    private EmployeeLeaveService employeeLeaveService;

    @Autowired
    private EntityManagerFactory emf;
    
    Statistics getStatistics() {
        SessionFactory sessionFactory = emf.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();

        statistics.setStatisticsEnabled(true);
        return statistics;
    }
    
    @Test
    void employee_cache_test() {
        Statistics statistics = getStatistics();
        statistics.clear();

        employeeService.getMyInfo(1L);

        long first = statistics.getPrepareStatementCount();

        statistics.clear();

        employeeService.getMyInfo(1L);

        long second = statistics.getPrepareStatementCount();

        assertTrue(first > 0);
        assertEquals(0, second);
    }
    
    void calculateCurrYearLeaveDays(LocalDate hireDate, LocalDate now, int excepted) {
	    assertEquals(excepted, employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate, now));
    }
	
	@Test
	void calculateCurrYearLeaveDays_whenHireDateIsEndOfMonth_handlesMonthlyLeaveCorrectly() {
        calculateCurrYearLeaveDays(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 3, 31), 2);
	}
	
	@Test
	void calculateCurrYearLeaveDays_whenHireDateIsLeapDay_handlesMonthlyLeaveCorrectly() {
        calculateCurrYearLeaveDays(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 5, 29), 3);
	}
	
    @Test
    void calculateCurrYearLeaveDays_whenEmployeeHasLessThanOneYearService_returnsMonthlyLeaveDays() {
        calculateCurrYearLeaveDays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1), 5);
    }
    
    @Test
    void calculateCurrYearLeaveDays_whenEmployeeHasElevenMonthsService_returnsMaximumMonthlyLeaveDays() {
        calculateCurrYearLeaveDays(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 1), 11);
    }

    @Test
    void calculateCurrYearLeaveDays_whenEmployeeCompletesOneYearService_returnsBaseLeaveDays() {
        calculateCurrYearLeaveDays(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), 15);
    }
    
    @Test
    void calculateCurrYearLeaveDays_whenEmployeeHasTwoYearsService_returnsBaseLeaveDays() {
        calculateCurrYearLeaveDays(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1), 15);
    }

    @Test
    void calculateCurrYearLeaveDays_whenEmployeeHasThreeYearsService_returnsAdditionalLeaveDays() {
        LocalDate hireDate = LocalDate.of(2023, 1, 1);
        LocalDate now = LocalDate.of(2026, 1, 1);

        // 15 + ((3 - 1) / 2 * 1) = 16
        calculateCurrYearLeaveDays(hireDate, now, 16);
	    
	    hireDate = hireDate.plusMonths(2);
	    // 회계연도 규칙대로면 hireDate가 1월 1일로 보정되기 때문에 3년차로 인정받아서 16, 입사일 기준 계산 규칙이면 만 3년이 충족되지 않았으므로 15.
        calculateCurrYearLeaveDays(hireDate, now, CommonConfig.USE_FISCAL_YEAR_LEAVE_POLICY ? 16 : 15);
    }

    @Test
    void calculateCurrYearLeaveDays_whenEmployeeExceedsMaximumLeaveDays_returnsMaximumLeaveDays() {
    	calculateCurrYearLeaveDays(LocalDate.of(1990, 1, 1), LocalDate.of(2026, 1, 1), 25);
    }

}
