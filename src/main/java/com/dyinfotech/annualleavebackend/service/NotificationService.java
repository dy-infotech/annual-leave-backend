package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.domain.FcmToken;
import com.dyinfotech.annualleavebackend.repository.FcmTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
	private final FcmTokenRepository tokenRepository;
    private final FcmService fcmService;
    
    private final Clock clock;
    
    /**
     * ① 로그인 및 토크 동기화
     */
    @Transactional
    public void syncToken(Long employeeId, String fcmToken, String deviceOs) {
    	// 기기 재사용 및 소유자 변경 케이스 추적
    	tokenRepository.findByToken(fcmToken).ifPresent(existingToken -> {
    	    Long oldEmployeeId = existingToken.getEmployeeId();
    	    if (!oldEmployeeId.equals(employeeId)) {
    	        log.info("기기 소유자 변경 감지 (이전 사번 ID: {} -> 신규 사번 ID: {})", oldEmployeeId, employeeId);
    	        fcmService.unsubscribeTopics(fcmToken, oldEmployeeId);
    	    }
    	});
    	
        // 무조건 DB에 수정을 시도하여 updated_at을 현재 시간(LocalDateTime.now(Clock))으로 갱신
        int updatedRows = tokenRepository.updateTokenAndTouch(
                employeeId, 
                deviceOs, 
                LocalDateTime.now(clock), 
                fcmToken
        );
        
        // 업데이트된 행이 0개라는 것은 DB에 이 토큰이 없다는 뜻이므로 완전히 새로운 토큰으로 생성(INSERT)
        if (updatedRows == 0) {
            FcmToken newToken = new FcmToken(employeeId, fcmToken, deviceOs);
            tokenRepository.save(newToken);
        }

        // 구글 서버 토픽 구독은 비동기로 처리하여 로그인 API 지연 방지
        fcmService.subscribeTopics(fcmToken, employeeId);	// Employee::approverId가 Team::projectManagerId이므로 해당 팀의 PM의 Employee.employeeId.
    }

    /**
     * ② 로그아웃 및 기기 해제
     * TODO: 로그아웃 기능 구현 및 토큰 삭제 적용
     */
    @Transactional
    public void logoutToken(String fcmToken, Long employeeId) {
        // DB에서 삭제
        tokenRepository.deleteByToken(fcmToken);
        
        // 구글 서버에 토픽 해제 요청
        fcmService.unsubscribeTopics(fcmToken, employeeId);	// Employee::approverId가 Team::projectManagerId이므로 해당 팀의 PM의 Employee.employeeId.
    }

    /**
     * ③ 알림 발송 공통 메서드
     */
    public void sendNotificationToTeams(Collection<Long> approverIds, String title, String body) {
        fcmService.sendConditionNotification(approverIds, title, body);
    }
}
