package com.dyinfotech.annualleavebackend.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.TopicManagementResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {
	public static final String TEAM_TOPIC_PREFIX = "team_";
	
	private final FirebaseMessaging firebaseMessaging;
	
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
	// 직원 토큰을 해당 관리자 토픽에 연결한다
	public CompletableFuture<Boolean> subscribeTopics(String fcmToken, Long approverId) {
		try {
			List<String> tokens = Collections.singletonList(fcmToken);
			TopicManagementResponse result =
					firebaseMessaging.subscribeToTopic(tokens, TEAM_TOPIC_PREFIX + approverId);
			boolean success = result.getSuccessCount() == 1 && result.getFailureCount() == 0;
			if (success) {
				log.info("FCM 토픽 구독 성공 - Token: {}, Team: {}", maskFcmToken(fcmToken), approverId);
			} else {
				log.warn("FCM 토픽 구독 부분 실패 - Token: {}, Team: {}, successCount={}, failureCount={}",
						maskFcmToken(fcmToken), approverId, result.getSuccessCount(), result.getFailureCount());
			}
			return CompletableFuture.completedFuture(success);
		} catch (Exception e) {
			log.error("FCM 토픽 구독 중 오류 발생", e);
			return CompletableFuture.completedFuture(Boolean.FALSE);
		}
	}
	
	@Async("fcmExecutor")
	// 직원 토큰에서 해당 관리자 토픽 연결을 해제한다
	public CompletableFuture<Boolean> unsubscribeTopics(String fcmToken, Long approverId) {
		try {
			List<String> tokens = Collections.singletonList(fcmToken);
			TopicManagementResponse result =
					firebaseMessaging.unsubscribeFromTopic(tokens, TEAM_TOPIC_PREFIX + approverId);
			boolean success = result.getSuccessCount() == 1 && result.getFailureCount() == 0;
			if (success) {
				log.info("FCM 토픽 해제 성공 - Token: {}, Team: {}", maskFcmToken(fcmToken), approverId);
			} else {
				log.warn("FCM 토픽 해제 부분 실패 - Token: {}, Team: {}, successCount={}, failureCount={}",
						maskFcmToken(fcmToken), approverId, result.getSuccessCount(), result.getFailureCount());
			}
			return CompletableFuture.completedFuture(success);
		} catch (Exception e) {
			log.error("FCM 토픽 해제 중 오류 발생", e);
			return CompletableFuture.completedFuture(Boolean.FALSE);
		}
	}
	
	@Async("fcmExecutor")
	public void sendConditionNotification(Collection<Long> approverIds, String title, String body) {
		sendConditionNotificationNow(approverIds, title, body);
	}

	/**
	 * 비동기 executor가 포화된 경우 retryExecutor에서 직접 호출할 수 있는 동기 발송 경로.
	 * 각 partition 실패는 로그로 격리해 휴가 신청 커밋 결과에 영향을 주지 않는다.
	 */
	public void sendConditionNotificationNow(Collection<Long> approverIds, String title, String body) {
		if (approverIds == null || approverIds.isEmpty()) {
			return;
		}

		final int maxTopicCount = 5;
		List<Long> ids = new ArrayList<>(approverIds);
		for (int from = 0; from < ids.size(); from += maxTopicCount) {
			List<Long> partition = ids.subList(from, Math.min(from + maxTopicCount, ids.size()));
			String condition = partition.stream()
					.map(id -> "'" + TEAM_TOPIC_PREFIX + id + "' in topics")
					.collect(Collectors.joining(" || "));

			Message message = Message.builder()
					.setNotification(Notification.builder().setTitle(title).setBody(body).build())
					.setCondition(condition)
					.build();
			try {
				String response = firebaseMessaging.send(message);
				log.info("조건부 알림 발송 성공 (대상: {}명): {}", partition.size(), response);
			} catch (Exception e) {
				log.error("조건부 알림 발송 실패 (대상: {})", partition, e);
			}
		}
	}

}
