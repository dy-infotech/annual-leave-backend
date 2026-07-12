package com.dyinfotech.annualleavebackend.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FcmService {
	public static final String TEAM_TOPIC_PREFIX = "team_";
	
	@Async("fcmExecutor") // 별도의 스레드 풀 사용 권장
	public void subscribeTopics(String fcmToken, String teamId) {
		try {
			List<String> tokens = Collections.singletonList(fcmToken);
//			FirebaseMessaging.getInstance().subscribeToTopic(tokens, "all");
			FirebaseMessaging.getInstance().subscribeToTopic(tokens, TEAM_TOPIC_PREFIX + teamId);
			log.info("FCM 토픽 구독 성공 - Token: {}, Team: {}", fcmToken, teamId);
		} catch (Exception e) {
			log.error("FCM 토픽 구독 중 오류 발생", e);
		}
	}
	
	@Async("fcmExecutor")
	public void unsubscribeTopics(String fcmToken, String teamId) {
		try {
			List<String> tokens = Collections.singletonList(fcmToken);
//			FirebaseMessaging.getInstance().unsubscribeFromTopic(tokens, "all");
			FirebaseMessaging.getInstance().unsubscribeFromTopic(tokens, TEAM_TOPIC_PREFIX + teamId);
			log.info("FCM 토픽 해제 성공 - Token: {}, Team: {}", fcmToken, teamId);
		} catch (Exception e) {
			log.error("FCM 토픽 해제 중 오류 발생", e);
		}
	}
	
	@Async("fcmExecutor")
	public void switchTeamTopic(List<String> tokens, String oldTeamId, String newTeamId) {
		try {
			if (tokens.isEmpty()) return;
			
			// 1. 이전 팀 토픽 해제
			FirebaseMessaging.getInstance().unsubscribeFromTopic(tokens, TEAM_TOPIC_PREFIX + oldTeamId);
			
			// 2. 새로운 팀 토픽 구독
			FirebaseMessaging.getInstance().subscribeToTopic(tokens, TEAM_TOPIC_PREFIX + newTeamId);
			
			log.info("인사이동 토픽 전환 완료 - 이전: {}, 신규: {}, 대상 토큰 수: {}", oldTeamId, newTeamId, tokens.size());
		} catch (Exception e) {
			log.error("인사이동 토픽 전환 중 오류 발생", e);
		}
		
	}
	
	@Async("fcmExecutor")
	public void sendConditionNotification(List<String> teamIds, String title, String body) {
		try {
			String condition = teamIds.stream()
					.map(id -> "'team_" + id + "' in topics")
					.collect(Collectors.joining(" || "));
			
			Message message = Message.builder()
					.setNotification(Notification.builder().setTitle(title).setBody(body).build())
					.setCondition(condition)
					.build();
			
			String response = FirebaseMessaging.getInstance().send(message);
			log.info("조건부 알림 발송 성공: {}", response);
		} catch (Exception e) {
			log.error("조건부 알림 발송 실패", e);
		}
	}
}
