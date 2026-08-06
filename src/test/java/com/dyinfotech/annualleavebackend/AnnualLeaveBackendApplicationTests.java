package com.dyinfotech.annualleavebackend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.dyinfotech.annualleavebackend.config.CommonConfig;
import com.dyinfotech.annualleavebackend.service.EmployeeLeaveService;

@SpringBootTest
class AnnualLeaveBackendApplicationTests {

	@Autowired
    private EmployeeLeaveService employeeLeaveService;
	
	@Test
	void calculateCurrYearLeaveDays_whenHireDateIsEndOfMonth_handlesMonthlyLeaveCorrectly() {
	    LocalDate hireDate = LocalDate.of(2025, 1, 31);
	    LocalDate now = LocalDate.of(2025, 3, 31);

	    float result = employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate, now);
	    assertEquals(2, result);
	}
	
	@Test
	void calculateCurrYearLeaveDays_whenHireDateIsLeapDay_handlesMonthlyLeaveCorrectly() {
	    LocalDate hireDate = LocalDate.of(2024, 2, 29);
	    LocalDate now = LocalDate.of(2024, 5, 29);

	    float result = employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate, now);

	    assertEquals(3, result);
	}
	
    @Test
    void calculateCurrYearLeaveDays_whenEmployeeHasLessThanOneYearService_returnsMonthlyLeaveDays() {
        LocalDate hireDate = LocalDate.of(2026, 1, 1);
        LocalDate now = LocalDate.of(2026, 6, 1);

        float result = employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate, now);

	    assertEquals(5, result);
    }
    
    @Test
    void calculateCurrYearLeaveDays_whenEmployeeHasElevenMonthsService_returnsMaximumMonthlyLeaveDays() {
        LocalDate hireDate = LocalDate.of(2025, 1, 1);
        LocalDate now = LocalDate.of(2025, 12, 1);

        float result = employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate, now);

	    assertEquals(11, result);
    }

    @Test
    void calculateCurrYearLeaveDays_whenEmployeeCompletesOneYearService_returnsBaseLeaveDays() {
        LocalDate hireDate = LocalDate.of(2025, 1, 1);
        LocalDate now = LocalDate.of(2026, 1, 1);

        float result = employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate, now);

	    assertEquals(15, result);
    }
    
    @Test
    void calculateCurrYearLeaveDays_whenEmployeeHasTwoYearsService_returnsBaseLeaveDays() {
        LocalDate hireDate = LocalDate.of(2024, 1, 1);
        LocalDate now = LocalDate.of(2026, 1, 1);

        float result = employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate, now);

	    assertEquals(15, result);
    }

    @Test
    void calculateCurrYearLeaveDays_whenEmployeeHasThreeYearsService_returnsAdditionalLeaveDays() {
        LocalDate hireDate = LocalDate.of(2023, 1, 1);
        LocalDate now = LocalDate.of(2026, 1, 1);

        float result = employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate, now);


        // 15 + ((3 - 1) / 2 * 1) = 16
	    assertEquals(16, result);
	    
	    hireDate = hireDate.plusMonths(2);
	    result = employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate, now);
	    
	    // 회계연도 규칙대로면 hireDate가 1월 1일로 보정되기 때문에 3년차로 인정받아서 16, 입사일 기준 계산 규칙이면 만 3년이 충족되지 않았으므로 15.
	    assertEquals(CommonConfig.USE_FISCAL_YEAR_LEAVE_POLICY ? 16 : 15, result);
    }

    @Test
    void calculateCurrYearLeaveDays_whenEmployeeExceedsMaximumLeaveDays_returnsMaximumLeaveDays() {
        LocalDate hireDate = LocalDate.of(1990, 1, 1);
        LocalDate now = LocalDate.of(2026, 1, 1);

        float result = employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate, now);

	    assertEquals(25, result);
    }

}
