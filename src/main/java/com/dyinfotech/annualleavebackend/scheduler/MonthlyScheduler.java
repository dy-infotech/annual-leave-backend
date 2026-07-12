package com.dyinfotech.annualleavebackend.scheduler;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.service.HolidaySyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyScheduler {
	
	private final HolidaySyncService holidaySyncService;
	
    /// 매월 1일 새벽 3시에 주기적으로 공휴일 정보 동기화
    @Scheduled(cron = "0 0 3 1 * ?") 
    public void monthlySchedule() {
    	LocalDate now = LocalDate.now();
        log.info("=== [월간 스케줄러] {}년 {}월 공휴일 재갱신 캐싱 시작 ===", now.getYear(), now.getMonthValue());
        holidaySyncService.syncHolidays(now.getYear(), now.getMonthValue());
        log.info("=== [월간 스케줄러] {}년 {}월 공휴일 재갱신 캐싱 완료 ===", now.getYear(), now.getMonthValue());
    }
}
