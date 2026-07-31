package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDate;

public interface HolidayRepositoryCustom {
	void deleteByHolidayDateBetween(LocalDate start, LocalDate end);
}
