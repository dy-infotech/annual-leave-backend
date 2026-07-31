package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.common.util.DateUtils;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Holiday;

public interface HolidayRepository extends HolidayJpaRepository {
	// 특정 연도의 데이터가 있는지 조회 (필요 시 활용)
	default boolean existsByYear(int startYear) {
	    Year year = Year.of(startYear);
	    return existsByHolidayDateBetween(
	    	DateUtils.getFirstDayOfYear(year), 
	    	DateUtils.getLastDayOfYear(year)
	    );
	}
	
	// 특정 연도의 전체 휴일 조회 (필요 시 활용)
	@Cacheable(value = CacheConfig.CACHE_HOLIDAYS, key = "#a0")
    default List<Holiday> findAllByYear(int startYear) {
    	Year year = Year.of(startYear);
        return findByHolidayDateBetween(
    	    DateUtils.getFirstDayOfYear(year), 
    	    DateUtils.getLastDayOfYear(year)
        );
    }

    // 두 개 년도 데이터 조회 (올해, 내년)
	@Cacheable(value = CacheConfig.CACHE_HOLIDAYS, key = "#a0 + '-' + #a1")
    default List<Holiday> findByYearRange(int startYear, int endYear) {
        return findByHolidayDateBetween(
    	    DateUtils.getFirstDayOfYear(Year.of(startYear)), 
    	    DateUtils.getLastDayOfYear(Year.of(endYear))
        );
    }

    // 매월 재갱신시 데이터 삭제 후 저장 (API 갱신용)
	// XXX: 스프링 프록시 내부 호출(Self-Invocation) 한계 때문에, 
	//		오버로딩된 모든 진입 메서드에 @Transactional과 @CacheEvict를 동일하게 작성해야 캐시가 정상 동작
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_HOLIDAYS, allEntries = true)
    default void replaceMonthlyHolidays(int startYear, int startMonth, List<Holiday> newHolidays) {
    	YearMonth yearMonth = YearMonth.of(startYear, startMonth);
    	replaceMonthlyHolidays(yearMonth, newHolidays);
    }
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_HOLIDAYS, allEntries = true)
    default void replaceMonthlyHolidays(YearMonth yearMonth, List<Holiday> newHolidays) {
    	replaceMonthlyHolidays(yearMonth.atDay(1), yearMonth.atEndOfMonth(), newHolidays);
    }
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_HOLIDAYS, allEntries = true)
    default void replaceMonthlyHolidays(LocalDate start, LocalDate end, List<Holiday> newHolidays) {
        deleteByHolidayDateBetween(start, end);
        if (!newHolidays.isEmpty()) {        	
        	saveAll(newHolidays);
        }
    }
}
