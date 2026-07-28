package com.dyinfotech.annualleavebackend.config;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class FirebaseConfig {
	@Value("${fcm.certification}")
    private Resource credentialResource;
	
	@PostConstruct
    public void initialize() {
		// 리소스 자체가 없으면 생략
		if (!credentialResource.exists()) {
			log.error("Firebase 키 파일을 찾을 수 없어 초기화를 스킵합니다. 경로를 확인하세요: {}", credentialResource);
			return;
		}
        try  (InputStream serviceAccount = credentialResource.getInputStream()) {
            // 1. 구글 인증 객체 생성
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            // 2. 파이어베이스 앱 초기화 (중복 초기화 방지)
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("FirebaseApp [DEFAULT] 초기화 성공!");
            }
        } catch (IOException e) {
            throw new RuntimeException("Firebase Admin SDK 초기화 실패", e);
        }
    }
	
	@Bean
	FirebaseMessaging firebaseMessaging() {
		if (FirebaseApp.getApps().isEmpty()) {
			// initialize()가 실패했거나 스킵되었을 경우 서버 시작 단계에서 빠르게 에러를 감지
			throw new IllegalStateException("FirebaseApp이 초기화되지 않아 FirebaseMessaging Bean을 생성할 수 없습니다.");
		}
		return FirebaseMessaging.getInstance();
	}
}
