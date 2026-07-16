package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.domain.Holiday;

interface HolidayJpaRepository extends JpaRepository<Holiday, LocalDate> {
	boolean existsByHolidayDateBetween(LocalDate start, LocalDate end);
	
	List<Holiday> findByHolidayDateBetween(LocalDate start, LocalDate end);
	
	@Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트를 비워야 데이터 꼬임 방지
	@Query("DELETE FROM Holiday h WHERE h.holidayDate BETWEEN :start AND :end")
	void deleteByHolidayDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
