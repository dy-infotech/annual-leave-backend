package com.dyinfotech.annualleavebackend.scheduler;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.service.EmployeeLeaveService;
import com.dyinfotech.annualleavebackend.service.HolidaySyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class YearlyScheduler {
    private final EmployeeLeaveService employeeLeaveService;
    private final HolidaySyncService holidaySyncService;
    private final BasisDataFactory basisDataFactory;
    
    private final Clock clock;
    
    /**
     * 매년 1월 1일 0시 0분 0초에 실행되는 연차 초기화 및 롤오버 스케줄러
     * 크론 표현식: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0 0 1 1 ?") 
    public void yearlySchedule() {
    	log.info("=== [연간 스케줄러] 기초데이터 팩토리 리로드 시작 ===");
        try {
            basisDataFactory.reload();
            log.info("=== [연간 스케줄러] 기초데이터 팩토리 리로드 완료 ===");
        } catch (Exception e) {
            log.error("=== [연간 스케줄러] 기초데이터 팩토리 리로드 중 예외 발생 (스케줄러는 계속 진행합니다) ===", e);
        }
        
        log.info("=== [연간 스케줄러] 새해 맞이 전직원 연차 롤오버 및 재계산 시작 ===");
        LocalDate now = LocalDate.now(clock);
        int currentYear = now.getYear();
        String currentYearStr = String.valueOf(now.getYear());
        // 메서드 내부에서 예외를 잡더라도, DB 커넥션 장애나 findAll 조회 자체에서 에러가 터지면
        // 밖으로 예외가 튀어 나와 아래 로직이 멈추므로 try-catch 처리
        try {
            employeeLeaveService.renewAllActiveEmployeesLeave(currentYearStr);
            log.info("=== [연간 스케줄러] 전직원 연차 갱신 프로세스 완료 ===");
        } catch (Exception e) {
            log.error("=== [연간 스케줄러] 전직원 연차 갱신 프로세스 전체 실패 (공휴일 동기화는 강제로 계속 진행합니다) ===", e);
        }
        
        log.info("=== [연간 스케줄러] {}년 전체 공휴일 캐싱 시작 ===", currentYearStr);
        
        // 올해와 내년치 데이터 처리
    	for (int year = 0; year <= 1; ++year) {
			setSpecialDays(currentYear + year);
        }
        
        log.info("=== [연간 스케줄러] {}년 전체 공휴일 캐싱 완료 ===", currentYear);
    }
    
    private void setSpecialDays(int year) {
    	Flux.range(1, 12) 
	        .flatMap(m -> holidaySyncService.fetchHolidaysFromApi(year, m) // 여러 달을 병렬로 요청
	        								.flatMap(holidays -> holidaySyncService.deleteAndSaveHolidays(year, m, holidays))
	        )
	        .then()
	        .block();
    }
}