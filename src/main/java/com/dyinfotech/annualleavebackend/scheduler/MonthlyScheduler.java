package com.dyinfotech.annualleavebackend.scheduler;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.config.TimeConfig;
import com.dyinfotech.annualleavebackend.service.FcmService;
import com.dyinfotech.annualleavebackend.service.HolidaySyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyScheduler {
	
	private final HolidaySyncService holidaySyncService;
	private final FcmService fcmService;
	
	private final Clock clock;
	
    // 매월 1일 새벽 3시에 주기적으로 공휴일 정보 동기화
    @Scheduled(cron = "0 0 3 1 * ?", zone = TimeConfig.TIME_ZONE) 
    public void monthlySchedule() {
    	LocalDate now = LocalDate.now(clock);
        log.info("=== [월간 스케줄러] {}년 {}월 공휴일 재갱신 캐싱 시작 ===", now.getYear(), now.getMonthValue());
        try {
        	holidaySyncService.fetchHolidaysFromApi(now.getYear(), now.getMonthValue())
					        	.flatMap(holidays -> 
					        		holidaySyncService.deleteAndSaveHolidays(
					        				now.getYear(), 
					        				now.getMonthValue(), 
					                        holidays
					               )
					        	)
					        	.then()
					        	.block();
            log.info("=== [월간 스케줄러] {}년 {}월 공휴일 재갱신 캐싱 완료 ===", now.getYear(), now.getMonthValue());
        } catch (Exception e) {
            log.error("=== [월간 스케줄러] {}년 {}월 공휴일 재갱신 중 예외 발생 (스케줄러는 계속 진행) ===", now.getYear(), now.getMonthValue(), e);
        }

        log.info("=== [월간 스케줄러] {}년 {}월 비활성 FCM Push 토큰 정리 시작 ===", now.getYear(), now.getMonthValue());
        try {
        	// TODO: 비활성 토큰 삭제 주기 기초데이터 전환 필요
        	fcmService.deleteInactiveToken(LocalDateTime.now(clock), 3);
            log.info("=== [월간 스케줄러] {}년 {}월 비활성 FCM Push 토큰 정리 완료 ===", now.getYear(), now.getMonthValue());
        } catch (Exception e) {
            log.error("=== [월간 스케줄러] {}년 {}월 비활성 FCM Push 토큰 정리 중 예외 발생 (스케줄러는 계속 진행) ===", now.getYear(), now.getMonthValue(), e);
        }
    }
}
