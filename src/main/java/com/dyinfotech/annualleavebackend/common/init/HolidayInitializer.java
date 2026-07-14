package com.dyinfotech.annualleavebackend.common.init;

import java.time.LocalDate;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.repository.HolidayRepository;
import com.dyinfotech.annualleavebackend.service.HolidaySyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HolidayInitializer implements ApplicationRunner {
	private final HolidaySyncService holidaySyncService;
    
	@Override
	public void run(ApplicationArguments args) throws Exception {
		// TODO Auto-generated method stub
		int currentYear = LocalDate.now().getYear();
        setSpecialDays(currentYear);
        setSpecialDays(currentYear + 1);
	}
	
	private void setSpecialDays(int year) {
        String yearStr = String.valueOf(year);

        log.info("=== [시스템 초기화] {}년 공휴일 데이터 존재 여부 검사 ===", year);
		// DB에 해당 년도 공휴일 데이터가 아예 비어있는지 체크
        if (holidaySyncService.findAllByYear(yearStr).isEmpty()) {
            log.info("=== [시스템 초기화] DB가 비어 있습니다. 공휴일 초기 동기화를 시작합니다. ===");
            
            for (int month = 1; month <= 12; month++) {
                try {
                    // API 호출 후 DB 저장
                    holidaySyncService.deleteAndSaveHolidays(
                        year, 
                        month, 
                        holidaySyncService.fetchHolidaysFromApi(year, month)
                    );
                } catch (Exception e) {
                    log.error("[시스템 초기화] {}년 {}월 공휴일 동기화 실패: {}", year, month, e.getMessage());
                }
            }
            log.info("=== [시스템 초기화] {}년 1~12월 공휴일 캐싱 완료 ===", year);
        } else {
            log.info("=== [시스템 초기화] 이미 DB에 데이터가 존재하므로 스킵합니다. ===");
        }
	}

}
