package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.Holiday;
import com.dyinfotech.annualleavebackend.repository.query.HolidayRepositoryCustom;

interface HolidayJpaRepository extends JpaRepository<Holiday, LocalDate>, HolidayRepositoryCustom {
	boolean existsByHolidayDateBetween(LocalDate start, LocalDate end);
	
	List<Holiday> findByHolidayDateBetween(LocalDate start, LocalDate end);
}
