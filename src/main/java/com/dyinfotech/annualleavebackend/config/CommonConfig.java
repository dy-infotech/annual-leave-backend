package com.dyinfotech.annualleavebackend.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

@Configuration
public class CommonConfig {
	// JSON 파싱용 ObjectMapper 등록
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
    
    @Bean
    WebClient.Builder webClientBuilder() {
    	return WebClient.builder();
    }
    
    // 기본 타임아웃(10초)이 적용된 전역 WebClient 등록
    @Bean
    @Primary
    WebClient webClient(WebClient.Builder builder) {
        // Reactor Netty의 HttpClient 설정
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)	// 연결 타임아웃 10초
                .responseTimeout(Duration.ofSeconds(10));				// 응답 타임아웃 10초

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
