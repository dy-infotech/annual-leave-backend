package com.dyinfotech.annualleavebackend.common.util;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;

public class DateUtils {
	public static final LocalDate getFirstDayOfYear(Year year) {
		return year.atDay(1);
	}
	public static final LocalDate getLastDayOfYear(Year year) {
		return year.atMonth(Month.DECEMBER).atEndOfMonth();
	}
}
