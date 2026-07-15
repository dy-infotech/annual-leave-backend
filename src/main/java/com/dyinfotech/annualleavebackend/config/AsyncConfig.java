package com.dyinfotech.annualleavebackend.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
	// FcmService에서 명시한 "fcmExecutor" 빈을 정의합니다.
    @Bean(name = "fcmExecutor")
    Executor fcmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 스레드 풀 설정을 사내 규모 및 알림 빈도에 맞게 튜닝합니다.
        executor.setCorePoolSize(5);       // 기본적으로 유지할 스레드 개수
        executor.setMaxPoolSize(20);       // 최대 트래픽일 때 확장될 스레드 개수
        executor.setQueueCapacity(500);    // 스레드가 꽉 찼을 때 대기할 작업 큐 크기
        
        // 로그에서 비동기 스레드를 쉽게 식별하기 위해 접두사 설정
        executor.setThreadNamePrefix("fcm-");
        
        // 애플리케이션 종료 시 큐에 남아있는 작업이 다 끝날 때까지 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
}
