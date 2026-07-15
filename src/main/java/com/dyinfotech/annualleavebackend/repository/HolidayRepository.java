package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.dyinfotech.annualleavebackend.domain.Holiday;

public interface HolidayRepository extends HolidayJpaRepository {
	// 특정 연도의 전체 휴일 조회 (필요 시 활용)
    default List<Holiday> findAllByYear(int year) {
        return findByHolidayDateBetween(
            LocalDate.of(year, 1, 1), 
            LocalDate.of(year, 12, 31)
        );
    }

    // 두 개 년도 데이터 조회 (올해, 내년)
    default List<Holiday> findByYearRange(int startYear, int endYear) {
        return findByHolidayDateBetween(
            LocalDate.of(startYear, 1, 1), 
            LocalDate.of(endYear, 12, 31)
        );
    }

    // 매월 재갱신시 데이터 삭제 후 저장 (API 갱신용)
    default void replaceMonthlyHolidays(int startYear, int startMonth, List<Holiday> newHolidays) {
    	YearMonth yearMonth = YearMonth.of(startYear, startMonth);
    	replaceMonthlyHolidays(yearMonth, newHolidays);
    }
    default void replaceMonthlyHolidays(YearMonth yearMonth, List<Holiday> newHolidays) {
    	replaceMonthlyHolidays(yearMonth.atDay(1), yearMonth.atEndOfMonth(), newHolidays);
    }
    default void replaceMonthlyHolidays(LocalDate start, LocalDate end, List<Holiday> newHolidays) {
        deleteByHolidayDateBetween(start, end);
        if (!newHolidays.isEmpty()) {        	
        	saveAll(newHolidays);
        }
    }
}
