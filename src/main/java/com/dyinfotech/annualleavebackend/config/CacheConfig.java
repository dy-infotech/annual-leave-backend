package com.dyinfotech.annualleavebackend.config;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.repository.DepartmentRepository;
import com.dyinfotech.annualleavebackend.repository.TeamManagerRepository;
import com.dyinfotech.annualleavebackend.repository.TeamRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

@Configuration
@EnableCaching
public class CacheConfig {
	
	// 어노테이션에서 사용할 상수를 선언 (컴파일 타임 상수)
    public static final String CACHE_HOLIDAYS = "holidays";
    public static final String CACHE_EMPLOYEES = "employees";
    public static final String CACHE_TEAM = "team";
    public static final String CACHE_TEAM_MANAGEMENT_DATA = "teamManagementData";
    public static final String CACHE_DEPARTMENT = "department";
    public static final String TOTAL_KEY = "total";

    
    public static final Cache<String, List<String>> EMAIL_BY_NAME_CACHE = Caffeine.newBuilder()
																                    .maximumSize(20_000)
																                    .expireAfterWrite(Duration.ofHours(1))
																                    .build();

    public static final Cache<String, String> EMAIL_BY_EMPLOYEE_NUMBER_CACHE = Caffeine.newBuilder()
																		               .maximumSize(20_000)
																		               .expireAfterWrite(Duration.ofHours(1))
																		               .build();
    
	
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

	    // 3. 팀 관리 캐시 설정 (24시간 만료)
	    CaffeineCache teamManagementCache = new CaffeineCache(CACHE_TEAM_MANAGEMENT_DATA, 
	        Caffeine.newBuilder()
	            .expireAfterWrite(24, TimeUnit.HOURS)
	            .maximumSize(1000)
	            .build());

	    // 생성한 캐시들을 리스트로 묶어서 매니저에 등록
	    cacheManager.setCaches(List.of(holidaysCache, userCache, teamManagementCache));
	    
	    return cacheManager;
	}
	
	@Bean("teamLoadingCache")
	LoadingCache<String, List<Team>> teamLoadingCache(TeamRepository teamRepository) {
	    return Caffeine.newBuilder()
	            .maximumSize(100)
	            .expireAfterWrite(24, TimeUnit.HOURS)
	            .build(key -> {
	                if (TOTAL_KEY.equals(key)) {
	                    return teamRepository.findAllByEnabledTrue();
	                }
	                
	                return teamRepository.findByTeamNameAndEnabledTrue(key)
				                        .map(Collections::singletonList)
				                        .orElseGet(Collections::emptyList);
	            });
	}
	
	@Bean("teamManagerLoadingCache")
	LoadingCache<String, List<TeamManager>> teamManagerLoadingCache(TeamManagerRepository teamManagerRepository) {
	    return Caffeine.newBuilder()
			            .maximumSize(100)
			            .expireAfterWrite(24, TimeUnit.HOURS)
			            .build(key -> {
			                // 캐시에는 LAZY 연관까지 초기화된 엔티티만 담는다.
			                // (세션이 닫힌 뒤 다른 요청이 탐색하면 LazyInitializationException)
			                if (TOTAL_KEY.equals(key)) {
			                    return teamManagerRepository.findAllWithAssociations();
			                }

			                return teamManagerRepository.findAllByTeamNameWithAssociations(key);
			            });
	}
	
	@Bean("departmentLoadingCache")
	LoadingCache<String, List<Department>> departmentLoadingCache(DepartmentRepository departmentRepository) {
	    return Caffeine.newBuilder()
	            .maximumSize(100)
	            .expireAfterWrite(24, TimeUnit.HOURS)
	            .build(key -> {
	                if (TOTAL_KEY.equals(key)) {
	                    return departmentRepository.findAllByEnabledTrue();
	                }
	                
	                return departmentRepository.findByDepartmentNameAndEnabledTrue(key)
				                        .map(Collections::singletonList)
				                        .orElseGet(Collections::emptyList);
	            });
	}
}
