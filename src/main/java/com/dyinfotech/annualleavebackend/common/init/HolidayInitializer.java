package com.dyinfotech.annualleavebackend.common.init;

import java.time.Clock;
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
	private final Clock clock;
    
	@Override
	public void run(ApplicationArguments args) throws Exception {
		// TODO Auto-generated method stub
		int currentYear = LocalDate.now(clock).getYear();
		
		// CompletableFuture를 사용하여 별도의 백그라운드 스레드에서 비동기로 실행합니다.
        // 이로 인해 스프링 컨텍스트는 대기하지 않고 즉시 기동을 완료합니다.
        CompletableFuture.runAsync(() -> {
            log.info("=== [시스템 초기화] 백그라운드 공휴일 동기화 스레드 시작 ===");
            
            // 올해와 내년은 매 기동 시 12개월 전체를 재동기화한다.
            // 월별 fetch가 성공한 뒤에만 해당 월을 replace하므로 일부 월 실패가
            // 다음 기동에서 영구적으로 skip되는 상태를 만들지 않는다.
            syncYear(currentYear);
            syncYear(currentYear + 1);
            
            log.info("=== [시스템 초기화] 백그라운드 공휴일 동기화 완료 ===");
        }).exceptionally(ex -> {
            log.error("=== [시스템 초기화] 백그라운드 동기화 중 에러 발생 ===", ex);
            return null;
        });
	}
    void syncYear(int year) {
        log.info("=== [시스템 초기화] {}년 1~12월 공휴일 동기화 시작 ===", year);

        Flux.range(1, 12)
            .flatMap(
                month -> holidaySyncService.fetchHolidaysFromApi(year, month)
                    .flatMap(holidays ->
                        holidaySyncService.deleteAndSaveHolidays(year, month, holidays)),
                3
            )
            .then()
            .block();

        holidaySyncService.findAllByYear(year);
        log.info("=== [시스템 초기화] {}년 1~12월 공휴일 동기화 완료 ===", year);
    }

}
