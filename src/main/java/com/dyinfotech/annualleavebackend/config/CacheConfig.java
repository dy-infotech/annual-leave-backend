package com.dyinfotech.annualleavebackend.config;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class CacheConfig {
	
	// 어노테이션에서 사용할 상수를 선언 (컴파일 타임 상수)
    public static final String CACHE_HOLIDAYS = "holidays";
    public static final String CACHE_EMPLOYEES = "employees";
    public static final String CACHE_TEAMS = "teams";
	
	@Bean
	CacheManager cacheManager() {
	    SimpleCacheManager cacheManager = new SimpleCacheManager();
	    
	    // 1. 공휴일 캐시 설정 (24시간 만료)
	    CaffeineCache holidaysCache = new CaffeineCache(CACHE_HOLIDAYS, 
	        Caffeine.newBuilder()
	            .expireAfterWrite(24, TimeUnit.HOURS)
	            .maximumSize(100)
	            .build());

	    // 2. 근로자 캐시 설정 (30분 만료)
	    CaffeineCache userCache = new CaffeineCache(CACHE_EMPLOYEES, 
	        Caffeine.newBuilder()
	            .expireAfterWrite(30, TimeUnit.MINUTES)
	            .maximumSize(1000)
	            .build());

	    // 3. 팀 캐시 설정 (24시간 만료)
	    CaffeineCache teamCache = new CaffeineCache(CACHE_TEAMS, 
	        Caffeine.newBuilder()
	            .expireAfterWrite(24, TimeUnit.HOURS)
	            .maximumSize(20)
	            .build());

	    // 생성한 캐시들을 리스트로 묶어서 매니저에 등록
	    cacheManager.setCaches(List.of(holidaysCache, userCache, teamCache));
	    
	    return cacheManager;
	}
}
