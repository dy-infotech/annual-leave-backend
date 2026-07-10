package com.dyinfotech.annualleavebackend.scheduler;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.service.EmployeeLeaveService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveRolloverScheduler {

    private final EmployeeRepository employeeRepository;
    private final EmployeeLeaveService employeeLeaveService;

    /**
     * 매년 1월 1일 0시 0분 0초에 실행되는 연차 초기화 및 롤오버 스케줄러
     * 크론 표현식: 초 분 시 일 월 요일
     * XXX: 현재는 단일 서버 인스턴스 구동을 가정하고 설계되었습니다.
     * 		만약 향후 인프라를 다중화(Scale-out)하여 서버를 2대 이상 띄우게 될 경우,
     * 		새해 정각에 모든 서버가 이 크론(Cron)을 동시에 실행하므로 DB 데이터 중복 업데이트 및 락(Lock) 문제가 발생합니다.
     * 		인프라 확장 시, ShedLock(분산 락) 라이브러리를 도입하거나 별도의 배치를 구축해야 합니다.
     */
    @Transactional // ✨ 여러 직원의 데이터를 변경하므로 쓰기 트랜잭션 필수!
    @Scheduled(cron = "0 0 0 1 1 ?") 
    public void rolloverNewYearLeave() {
        log.info("=== [스케줄러] 새해 맞이 전직원 연차 롤오버 및 재계산 시작 ===");
        LocalDate now = LocalDate.now();
        String currentYear = String.valueOf(now.getYear());
        // 1. 퇴사자를 제외한 전직원 목록 조회 (필요 시 패치 조인이나 벌크 연산 고려)
        List<Employee> activeEmployees = employeeRepository.findAllByFireDateIsNull();
        
        // 2. 루프를 돌며 안전하게 연차 갱신
        for (Employee employee : activeEmployees) {
            try {
                // 기존에 만들어두신 연차 계산 및 setter 세팅 로직 재활용
            	String prevYear = employee.getCurrYear();
            	if (prevYear != null && prevYear != currentYear) {
            		employee.setPrevYear(prevYear);
            		employee.setPrevYearLeaveDays(employee.getCurrTotalLeaveDays());
            		employee.setCurrYear(currentYear);
            		employee.setCurrYearLeaveDays(employeeLeaveService.getCalculatedCurrYearLeaveDays(employee));
                    log.info("직원 번호 [{}] 연차 갱신 완료", employee.getEmployeeNumber());
            	} else {
            		log.error("직원 번호 [{}] 연차 갱신 실패", employee.getEmployeeNumber());
            	}
            } catch (Exception e) {
                // 한 명이 에러 나도 다른 직원들은 갱신되어야 하므로 예외 처리 개별 적용
                log.error("직원 번호 [{}] 연차 갱신 중 에러 발생: {}", employee.getEmployeeNumber(), e.getMessage());
            }
        }
        
        log.info("=== [스케줄러] 전직원 연차 갱신 프로세스 완료 ===");
    }
}