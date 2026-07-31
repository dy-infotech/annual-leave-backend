package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.Holiday;
import com.dyinfotech.annualleavebackend.repository.query.HolidayRepositoryCustom;

interface HolidayJpaRepository extends JpaRepository<Holiday, LocalDate>, HolidayRepositoryCustom {
	boolean existsByHolidayDateBetween(LocalDate start, LocalDate end);
	
	List<Holiday> findByHolidayDateBetween(LocalDate start, LocalDate end);
	
//	@Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트를 비워야 데이터 꼬임 방지
//	@Query("DELETE FROM Holiday h WHERE h.holidayDate BETWEEN :start AND :end")
//	void deleteByHolidayDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
