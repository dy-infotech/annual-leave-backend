package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;

import com.dyinfotech.annualleavebackend.domain.Holiday;

public interface HolidayRepository extends HolidayJpaRepository {
	// 특정 연도의 전체 휴일 조회 (필요 시 활용)
    default List<Holiday> findAllByYear(int startYear) {
    	Year year = Year.of(startYear);
        return findByHolidayDateBetween(
        	year.atDay(1),
        	year.atMonth(Month.DECEMBER).atEndOfMonth()
        );
    }

    // 두 개 년도 데이터 조회 (올해, 내년)
    default List<Holiday> findByYearRange(int startYear, int endYear) {
        return findByHolidayDateBetween(
        	Year.of(startYear).atDay(1),
        	Year.of(endYear).atMonth(Month.DECEMBER).atEndOfMonth()
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
