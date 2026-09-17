package com.dyinfotech.annualleavebackend.common.util;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;

import com.dyinfotech.annualleavebackend.config.CommonConfig;

import jakarta.validation.constraints.NotNull;

public class DateUtils {
	public static final LocalDate getFirstDayOfYear(Year year) {
		return year.atDay(1);
	}
	public static final LocalDate getLastDayOfYear(Year year) {
		return year.atMonth(Month.DECEMBER).atEndOfMonth();
	}
	
	public static final LocalDate getAnniversaryDate(LocalDate hireDate, int year) {
		if (hireDate.getMonthValue() == 2 && hireDate.getDayOfMonth() == 29 && !Year.isLeap(year)) {
			return LocalDate.of(year, 3, 1);
		}
		return hireDate.withYear(year);
	}
	
	public static final LocalDate getLeaveYearStartDate(LocalDate hireDate, LocalDate targetDate) {
		LocalDate anniversary = getAnniversaryDate(hireDate, targetDate.getYear());
		if (anniversary.isAfter(targetDate)) {
			anniversary = getAnniversaryDate(hireDate, targetDate.getYear() - 1);
		}
		return anniversary;
	}
	
	public static final LocalDate getLeaveYearEndDate(LocalDate hireDate, LocalDate targetDate) {
		LocalDate startDate = getLeaveYearStartDate(hireDate, targetDate);
		return getAnniversaryDate(hireDate, startDate.getYear() + 1).minusDays(1);
	}
	
	public static final int toMinutes(@NotNull Float useDays) {
		return Math.round(useDays * CommonConfig.DAILY_STANDARD_WORKING_MINUTES);
	}
}
