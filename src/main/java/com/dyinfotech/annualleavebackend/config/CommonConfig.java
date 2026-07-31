package com.dyinfotech.annualleavebackend.config;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class CommonConfig {
	// 회사 창립 기념일
	public static final LocalDateTime COMPANY_ANNIVERSARY = LocalDateTime.of(LocalDate.of(2011, 7, 8), LocalTime.MIN);
	// 일일 소정 근로시간은 8시간으로 간주한다. DB에서도 float 단위로 구현되어 있고, 2의 n승 분의 1 구조이기 때문에 부동 소수점을 유지했으므로 하드코딩한다.
	public static final int DAILY_STANDARD_WORKING_HOURS = 8;
	public static final int DAILY_STANDARD_WORKING_MINUTES = DAILY_STANDARD_WORKING_HOURS * 60;
	
    @Bean
    WebClient.Builder webClientBuilder() {
    	return WebClient.builder();
    }
    
    // 기본 타임아웃(10초)이 적용된 전역 WebClient 등록
    @Bean
    @Primary
    WebClient webClient(WebClient.Builder builder) {
//        // Reactor Netty의 HttpClient 설정
//        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient
//        																			.create()
//																	                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)	// 연결 타임아웃 10초
//																	                .responseTimeout(Duration.ofSeconds(10));				// 응답 타임아웃 10초
//
//        return builder
//                .clientConnector(new ReactorClientHttpConnector(httpClient))
//                .build();
    	// Java 11+ 내장 HttpClient 생성 및 설정 (타임아웃 등)
    	java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
														                .connectTimeout(Duration.ofSeconds(10))
														                .build();
        
        // Netty 대신 JdkClientHttpConnector를 사용하도록 WebClient 빌드
        return builder
                .clientConnector(new JdkClientHttpConnector(httpClient))
                .build();
    }
}
