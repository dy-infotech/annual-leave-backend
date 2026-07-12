package com.dyinfotech.annualleavebackend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.Holiday;

public interface HolidayRepository extends JpaRepository<Holiday, Holiday.HolidayId> {
	// 매월 재갱신할 때 해당 년/월 데이터만 싹 지우는 메서드
	void deleteByYearAndMonth(String year, String month);
	
	// 특정 연도의 전체 휴일 조회 (필요 시 활용)
    List<Holiday> findAllByYear(String year);
    
    // 특정 연도의 특정 월에 대한 전체 휴일 조회 (필요 시 활용)
    List<Holiday> findAllByYearAndMonth(String year, String month);

    // 특정 연도들의 전체 공휴일을 조회 (필요 시 활용)
    // 단, 백엔드단만 고려된 구문으로 프론트엔드에서는 현재 연도와 다음 연도에 대한 전체 휴일을 초기 캐싱하기 위해 백엔드에게 데이터 요청하는 정도로 쓰일 수 있음.
    List<Holiday> findAllByYearIn(Collection<String> years);
}
