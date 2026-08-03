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
	
	public static final int toMinutes(@NotNull Float useDays) {
		return Math.round(useDays * CommonConfig.DAILY_STANDARD_WORKING_MINUTES);
	}
}
