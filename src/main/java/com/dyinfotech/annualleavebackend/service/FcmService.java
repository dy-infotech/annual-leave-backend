package com.dyinfotech.annualleavebackend.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
import reactor.core.publisher.Flux;

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
	public CompletableFuture<Boolean> subscribeTopics(String fcmToken, Long approverId) {
		try {
			List<String> tokens = Collections.singletonList(fcmToken);
			firebaseMessaging.subscribeToTopic(tokens, TEAM_TOPIC_PREFIX + approverId);
			log.info("FCM 토픽 구독 성공 - Token: {}, Team: {}", maskFcmToken(fcmToken), approverId);
			return CompletableFuture.completedFuture(Boolean.TRUE);
		} catch (Exception e) {
			log.error("FCM 토픽 구독 중 오류 발생", e);
			return CompletableFuture.completedFuture(Boolean.FALSE);
		}
	}
	
	@Async("fcmExecutor")
	public CompletableFuture<Boolean> unsubscribeTopics(String fcmToken, Long approverId) {
		try {
			List<String> tokens = Collections.singletonList(fcmToken);
			firebaseMessaging.unsubscribeFromTopic(tokens, TEAM_TOPIC_PREFIX + approverId);
			log.info("FCM 토픽 해제 성공 - Token: {}, Team: {}", maskFcmToken(fcmToken), approverId);
			return CompletableFuture.completedFuture(Boolean.TRUE);
		} catch (Exception e) {
			log.error("FCM 토픽 해제 중 오류 발생", e);
			return CompletableFuture.completedFuture(Boolean.FALSE);
		}
	}
	
	@Async("fcmExecutor")
	public void sendConditionNotification(Collection<Long> approverIds, String title, String body) {
	    if (approverIds == null || approverIds.isEmpty()) {
	        return;
	    }
	    // topic 단위로 FCM Push를 보낼 경우 최대 5개의 topic만 전송 가능하다
	    int maxTopicCount = 5;
	    
//		// Iterator 기준 코드 (WebFlux 의존성 걷어낼 경우 필요한 코드)
//	    Iterator<Long> iterator = approverIds.iterator();
//
//	    // Iterator 순회 (모든 요소를 비울 때까지)
//		List<Long> partition = new ArrayList<>(maxTopicCount);
//	    while (iterator.hasNext()) {
//	        // 5개를 채우거나, 남은 데이터가 끝날 때까지 수집
//	        while (iterator.hasNext() && partition.size() < maxTopicCount) {
//	            partition.add(iterator.next());
//	        }
//
//	        // 5개 묶음(또는 남은 묶음) 발송 처리
//            String condition = partition.stream()
//                    .map(id -> "'" + TEAM_TOPIC_PREFIX + id + "' in topics")
//                    .collect(Collectors.joining(" || "));
//
//            Message message = Message.builder()
//                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
//                    .setCondition(condition)
//                    .build();
//	        try {
//	            String response = firebaseMessaging.send(message);
//	            log.info("조건부 알림 발송 성공 (대상: {}명): {}", partition.size(), response);
//	        } catch (Exception e) {
//	            // 특정 묶음 발송이 실패해도 다음 묶음 발송을 위해 예외 로그만 남기고 루프 계속 진행
//	            log.error("조건부 알림 발송 실패 (대상: {})", partition, e);
//	        } finally {
//	        	partition.clear();
//	        }
//	    }

	    Flux.fromIterable(approverIds)
	            .buffer(maxTopicCount)
	            .subscribe(partition -> {
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
	                    // 특정 5개 묶음 발송이 실패하더라도 다음 5개 묶음 발송은 계속 진행되도록 try-catch 감싸기
	                    log.error("조건부 알림 발송 실패 (대상: {})", partition, e);
	                }
	            });
	}
	
	@Transactional
	public void deleteInactiveToken(LocalDateTime now, int monthCount) {
		fcmTokenRepository.deleteByUpdatedAtBefore(now.minusMonths(monthCount));
	}
}
