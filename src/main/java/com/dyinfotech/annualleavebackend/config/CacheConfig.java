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

import com.dyinfotech.annualleavebackend.repository.DepartmentRepository;
import com.dyinfotech.annualleavebackend.repository.TeamManagerRepository;
import com.dyinfotech.annualleavebackend.repository.TeamRepository;
import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamManagerCacheRow;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_HOLIDAYS = "holidays";
    public static final String CACHE_EMPLOYEES = "employees";
    public static final String CACHE_TEAM_MANAGEMENT_DATA = "teamManagementData";
    public static final String TOTAL_KEY = "total";

    public record OrganizationCacheKey(boolean all, String name) {
        public static OrganizationCacheKey allRows() {
            return new OrganizationCacheKey(true, null);
        }

        public static OrganizationCacheKey byName(String name) {
            return new OrganizationCacheKey(false, name);
        }
    }

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

        CaffeineCache holidaysCache = new CaffeineCache(CACHE_HOLIDAYS,
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(100)
                        .build());

        CaffeineCache userCache = new CaffeineCache(CACHE_EMPLOYEES,
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .maximumSize(1000)
                        .build());

        CaffeineCache teamManagementCache = new CaffeineCache(CACHE_TEAM_MANAGEMENT_DATA,
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(1000)
                        .build());

        cacheManager.setCaches(List.of(holidaysCache, userCache, teamManagementCache));
        return cacheManager;
    }

    @Bean("teamLoadingCache")
    LoadingCache<OrganizationCacheKey, List<TeamCacheRow>> teamLoadingCache(TeamRepository teamRepository) {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .build(key -> {
                    if (key.all()) {
                        return teamRepository.findAllEnabledForCache();
                    }

                    return teamRepository.findByNameEnabledForCache(key.name())
                            .map(Collections::singletonList)
                            .orElseGet(Collections::emptyList);
                });
    }

    @Bean("teamManagerLoadingCache")
    LoadingCache<String, List<TeamManagerCacheRow>> teamManagerLoadingCache(TeamManagerRepository teamManagerRepository) {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .build(key -> {
                    if (TOTAL_KEY.equals(key)) {
                        return teamManagerRepository.findAllForCache();
                    }

                    return teamManagerRepository.findAllByTeamIdForCache(Long.valueOf(key));
                });
    }

    @Bean("departmentLoadingCache")
    LoadingCache<OrganizationCacheKey, List<DepartmentCacheRow>> departmentLoadingCache(DepartmentRepository departmentRepository) {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .build(key -> {
                    if (key.all()) {
                        return departmentRepository.findAllEnabledForCache();
                    }

                    return departmentRepository.findByNameEnabledForCache(key.name())
                            .map(Collections::singletonList)
                            .orElseGet(Collections::emptyList);
                });
    }
}
