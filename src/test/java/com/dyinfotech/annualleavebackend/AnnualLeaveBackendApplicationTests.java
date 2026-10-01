package com.dyinfotech.annualleavebackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.common.type.BasisDataType;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveAdjustmentRepository;
import com.dyinfotech.annualleavebackend.service.EmployeeLeaveService;
import com.dyinfotech.annualleavebackend.service.TeamService;

class AnnualLeaveBackendApplicationTests {

    private EmployeeLeaveService employeeLeaveService;

    @BeforeEach
    void setUp() {
        BasisDataFactory basisDataFactory = mock(BasisDataFactory.class);
        when(basisDataFactory.getAsInteger(BasisDataType.FIRST_YEAR_LEAVE_DAYS))
                .thenReturn(Optional.of(15));
        when(basisDataFactory.getAsInteger(BasisDataType.YEARS_PER_ADDITIONAL_LEAVE))
                .thenReturn(Optional.of(2));
        when(basisDataFactory.getAsInteger(BasisDataType.ADDITIONAL_LEAVE_DAYS))
                .thenReturn(Optional.of(1));
        when(basisDataFactory.getAsInteger(BasisDataType.MAXIMUM_LEAVE_DAYS))
                .thenReturn(Optional.of(25));

        employeeLeaveService = new EmployeeLeaveService(
                basisDataFactory,
                mock(LeaveAdjustmentRepository.class),
                mock(TeamService.class),
                mock(EmployeeRepository.class),
                mock(EmployeeCacheInvalidator.class),
                mock(PlatformTransactionManager.class),
                Clock.fixed(
                        Instant.parse("2026-09-28T00:00:00Z"),
                        ZoneId.of("Asia/Seoul")
                )
        );
    }

    private void assertCalculatedLeaveDays(LocalDate hireDate, LocalDate now, float expected) {
        assertEquals(expected, employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate, now));
    }

    @Test
    void calculateCurrYearLeaveDays_whenHireDateIsEndOfMonth_handlesMonthlyLeaveCorrectly() {
        assertCalculatedLeaveDays(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 3, 31), 2.0f);
    }

    @Test
    void calculateCurrYearLeaveDays_whenHireDateIsLeapDay_handlesMonthlyLeaveCorrectly() {
        assertCalculatedLeaveDays(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 5, 29), 3.0f);
    }

    @Test
    void calculateCurrYearLeaveDays_whenEmployeeHasLessThanOneYearService_returnsMonthlyLeaveDays() {
        assertCalculatedLeaveDays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1), 5.0f);
    }

    @Test
    void calculateCurrYearLeaveDays_whenEmployeeHasElevenMonthsService_returnsMaximumMonthlyLeaveDays() {
        assertCalculatedLeaveDays(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 1), 11.0f);
    }

    @Test
    void calculateCurrYearLeaveDays_whenEmployeeCompletesOneYearService_returnsBaseLeaveDays() {
        assertCalculatedLeaveDays(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), 15.0f);
    }

    @Test
    void calculateCurrYearLeaveDays_whenEmployeeHasTwoYearsService_returnsBaseLeaveDays() {
        assertCalculatedLeaveDays(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1), 15.0f);
    }

    @Test
    void calculateCurrYearLeaveDays_whenEmployeeHasThreeYearsService_returnsAdditionalLeaveDays() {
        LocalDate now = LocalDate.of(2026, 1, 1);

        assertCalculatedLeaveDays(LocalDate.of(2023, 1, 1), now, 16.0f);
        assertCalculatedLeaveDays(LocalDate.of(2023, 3, 1), now, 16.0f);
    }

    @Test
    void calculateCurrYearLeaveDays_whenEmployeeExceedsMaximumLeaveDays_returnsMaximumLeaveDays() {
        assertCalculatedLeaveDays(LocalDate.of(1990, 1, 1), LocalDate.of(2026, 1, 1), 25.0f);
    }
}
