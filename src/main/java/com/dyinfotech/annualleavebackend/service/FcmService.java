package com.dyinfotech.annualleavebackend.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.dyinfotech.annualleavebackend.repository.FcmTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {
	public static final String TEAM_TOPIC_PREFIX = "team_";
	
	private final FirebaseMessaging firebaseMessaging;
	private final FcmTokenRepository fcmTokenRepository;
	
	/**
	 * FCM Token의 중간 값들을 마스킹. 로그에 표기할 때 보안적으로 방어하기 위함.
	 * @param token
	 * @return maskedToken
	 */
	private String maskFcmToken(String token) {
	    if (token == null || token.isBlank()) {
	        return token;
	    }

	    int length = token.length();
	    
	    // 혹시 모를 아주 짧은 문자열에 대한 예외 처리 (최소 4자 이상일 때만 분할)
	    if (length < 4) {
	        return "****";
	    }

	    // 1/4 지점과 3/4 지점 계산
	    int start = length / 4;
	    int end = (length * 3) / 4;
	    int maskLength = end - start;

	    // 앞부분 + 마스킹(*) + 뒷부분 조합
	    return token.substring(0, start) 
	            + "*".repeat(maskLength) 
	            + token.substring(end);
	}
	
	@Async("fcmExecutor") // 별도의 스레드 풀 사용 권장
	public void subscribeTopics(String fcmToken, Long approverId) {
		try {
			List<String> tokens = Collections.singletonList(fcmToken);
//			firebaseMessaging.subscribeToTopic(tokens, "all");
			firebaseMessaging.subscribeToTopic(tokens, TEAM_TOPIC_PREFIX + approverId);
			log.info("FCM 토픽 구독 성공 - Token: {}, Team: {}", maskFcmToken(fcmToken), approverId);
		} catch (Exception e) {
			log.error("FCM 토픽 구독 중 오류 발생", e);
		}
	}
	
	@Async("fcmExecutor")
	public void unsubscribeTopics(String fcmToken, Long approverId) {
		try {
			List<String> tokens = Collections.singletonList(fcmToken);
//			firebaseMessaging.unsubscribeFromTopic(tokens, "all");
			firebaseMessaging.unsubscribeFromTopic(tokens, TEAM_TOPIC_PREFIX + approverId);
			log.info("FCM 토픽 해제 성공 - Token: {}, Team: {}", maskFcmToken(fcmToken), approverId);
		} catch (Exception e) {
			log.error("FCM 토픽 해제 중 오류 발생", e);
		}
	}
	
	@Async("fcmExecutor")
	public void sendConditionNotification(Collection<Long> approverIds, String title, String body) {
		try {
			String condition = approverIds.stream()
					.map(id -> "'team_" + id + "' in topics")
					.collect(Collectors.joining(" || "));
			
			Message message = Message.builder()
					.setNotification(Notification.builder().setTitle(title).setBody(body).build())
					.setCondition(condition)
					.build();
			
			String response = firebaseMessaging.send(message);
			log.info("조건부 알림 발송 성공: {}", response);
		} catch (Exception e) {
			log.error("조건부 알림 발송 실패", e);
		}
	}
	
	@Transactional
	public void deleteInactiveToken(LocalDateTime now, int N) {
		fcmTokenRepository.deleteByUpdatedAtBefore(now.minusMonths(N));
	}
}
