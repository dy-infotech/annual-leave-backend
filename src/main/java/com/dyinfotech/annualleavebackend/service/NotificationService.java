package com.dyinfotech.annualleavebackend.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.domain.FcmToken;
import com.dyinfotech.annualleavebackend.repository.FcmTokenRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {
	private final FcmTokenRepository tokenRepository;
    private final FcmService fcmService;
    
    /**
     * ① 로그인 및 토크 동기화 (UPSERT)
     */
    @Transactional
    public void syncToken(Long employeeId, String fcmToken, String deviceOs, String teamId) {
        // DB에 기존 토큰이 있는지 조회하여 애플리케이션 레벨 UPSERT 구현
        tokenRepository.findByFcmToken(fcmToken)
            .ifPresentOrElse(
                existingToken -> {
                    // 주인이 바뀌었거나 정보가 바뀌었다면 업데이트
                    existingToken.setEmployeeId(employeeId);
                    existingToken.setDeviceOs(deviceOs);
                },
                () -> {
                    // 완전히 새로운 토큰이면 인서트
                    FcmToken newToken = new FcmToken(employeeId, fcmToken, deviceOs);
                    tokenRepository.save(newToken);
                }
            );

        // 구글 서버 토픽 구독은 비동기로 처리하여 로그인 API 지연 방지
        fcmService.subscribeTopics(fcmToken, teamId);
    }

    /**
     * ② 로그아웃 및 기기 해제
     * TODO: 로그아웃 기능 구현 및 토큰 삭제 적용
     */
    @Transactional
    public void logoutToken(String fcmToken, String teamId) {
        // DB에서 삭제
        tokenRepository.deleteByFcmToken(fcmToken);
        
        // 구글 서버에 토픽 해제 요청
        fcmService.unsubscribeTopics(fcmToken, teamId);
    }

    /**
     * ③ 인사 이동 및 부서 변경 처리
     * TODO: 인사권자에 대한 Role 추가 및 해당 권한자가 프로젝트 매니저 이동시 해당 함수 적용
     */
    @Transactional(readOnly = true)
    public void handleHrMovement(Long employeeId, String oldTeamId, String newTeamId) {
        // 해당 유저가 가진 모든 기기 토큰 추출
        List<FcmToken> userTokens = tokenRepository.findByEmployeeId(employeeId);
        List<String> tokens = userTokens.stream()
                .map(FcmToken::getFcmToken)
                .collect(Collectors.toList());

        // 이전 팀 토픽 끊고 새 팀 토픽 연결 (비동기 묶음 처리)
        if (!tokens.isEmpty()) {
            fcmService.switchTeamTopic(tokens, oldTeamId, newTeamId);
        }
    }

    /**
     * ④ 알림 발송 공통 메서드
     */
    public void sendNotificationToTeams(List<String> teamIds, String title, String body) {
        fcmService.sendConditionNotification(teamIds, title, body);
    }
}
