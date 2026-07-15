package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.Holiday;

interface HolidayJpaRepository extends JpaRepository<Holiday, LocalDate> {
	List<Holiday> findByHolidayDateBetween(LocalDate start, LocalDate end);
	
	void deleteByHolidayDateBetween(LocalDate start, LocalDate end);
}
