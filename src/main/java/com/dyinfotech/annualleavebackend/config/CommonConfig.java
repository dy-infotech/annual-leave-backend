package com.dyinfotech.annualleavebackend.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class CommonConfig {
	// JSON 파싱용 ObjectMapper 등록
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
    
    // 타임아웃이 적용된 ClientHttpRequestFactory
    private SimpleClientHttpRequestFactory createRequestFactory(int connectTimeoutSeconds, int readTimeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(connectTimeoutSeconds).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(readTimeoutSeconds).toMillis());
        return factory;
    }
    
    @Bean
    RestClient.Builder restClientBuilder() {
    	return RestClient.builder();
    }
    
    // 기본 타임아웃(5초)이 적용된 전역 RestClient 등록
    @Bean
    @Primary // 나중에 다른 RestClient 빈이 생겨도 기본 주입되도록 보장
    RestClient restClient(RestClient.Builder builder) {
        // 스프링이 주입해주는 내장 builder를 사용해 깔끔하게 결합
        return builder
                .requestFactory(createRequestFactory(5, 5))
                .build();
    }
}
