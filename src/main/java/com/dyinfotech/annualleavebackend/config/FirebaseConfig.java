package com.dyinfotech.annualleavebackend.config;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;

@Configuration
public class FirebaseConfig {
	@Value("${fcm.certification}")
    private Resource credentialResource;
	
	@PostConstruct
    public void initialize() {
        try {
            // 1. 리소스 폴더에서 인증키 파일 읽기
            InputStream serviceAccount = credentialResource.getInputStream();

            // 2. 구글 인증 객체 생성
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            // 3. 파이어베이스 앱 초기화 (중복 초기화 방지)
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
        } catch (IOException e) {
            throw new RuntimeException("Firebase Admin SDK 초기화 실패", e);
        }
    }
}
