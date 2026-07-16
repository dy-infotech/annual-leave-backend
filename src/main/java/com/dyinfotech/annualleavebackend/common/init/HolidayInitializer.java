package com.dyinfotech.annualleavebackend.common.init;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.service.HolidaySyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class HolidayInitializer implements ApplicationRunner {
	private final HolidaySyncService holidaySyncService;
    
	@Override
	public void run(ApplicationArguments args) throws Exception {
		// TODO Auto-generated method stub
		int currentYear = LocalDate.now().getYear();
		
		// CompletableFuture를 사용하여 별도의 백그라운드 스레드에서 비동기로 실행합니다.
        // 이로 인해 스프링 컨텍스트는 대기하지 않고 즉시 기동을 완료합니다.
        CompletableFuture.runAsync(() -> {
            log.info("=== [시스템 초기화] 백그라운드 공휴일 동기화 스레드 시작 ===");
            
            // 올해와 내년 데이터를 순차적으로 세팅
            setSpecialDays(currentYear);
            setSpecialDays(currentYear + 1);
            
            log.info("=== [시스템 초기화] 백그라운드 공휴일 동기화 완료 ===");
        }).exceptionally(ex -> {
            log.error("=== [시스템 초기화] 백그라운드 동기화 중 에러 발생 ===", ex);
            return null;
        });
	}
	private void setSpecialDays(int year) {
        log.info("=== [시스템 초기화] {}년 공휴일 데이터 존재 여부 검사 ===", year);
		// DB에 해당 년도 공휴일 데이터가 아예 비어있는지 체크
        if (!holidaySyncService.existsByYear(year)) {
            log.info("=== [시스템 초기화] DB가 비어 있습니다. 공휴일 초기 동기화를 시작합니다. ===");
        	Flux.range(1, 12) 
    	        .flatMap(m -> holidaySyncService.fetchHolidaysFromApi(year, m) // 여러 달을 병렬로 요청
    	        								.flatMap(holidays -> holidaySyncService.deleteAndSaveHolidays(year, m, holidays))
    	        )
    	        .then()
    	        .block();
            log.info("=== [시스템 초기화] {}년 1~12월 공휴일 캐싱 완료 ===", year);
            
            // 캐시 저장
            holidaySyncService.findAllByYear(year);
        } else {
            log.info("=== [시스템 초기화] 이미 DB에 데이터가 존재하므로 스킵합니다. ===");
        }
	}

}
