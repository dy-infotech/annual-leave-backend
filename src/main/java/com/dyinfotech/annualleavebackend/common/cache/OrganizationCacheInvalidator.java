package com.dyinfotech.annualleavebackend.common.cache;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.common.transaction.AfterCommitExecutor;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.config.CacheConfig.OrganizationCacheKey;
import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamManagerCacheRow;
import com.github.benmanes.caffeine.cache.LoadingCache;

@Component
public class OrganizationCacheInvalidator {

    private final LoadingCache<OrganizationCacheKey, java.util.List<DepartmentCacheRow>> departmentCache;
    private final LoadingCache<OrganizationCacheKey, java.util.List<TeamCacheRow>> teamCache;
    private final LoadingCache<String, java.util.List<TeamManagerCacheRow>> teamManagerCache;
    private final CacheManager cacheManager;
    private final AfterCommitExecutor afterCommitExecutor;
    private final EmployeeViewCacheKey employeeViewCacheKey;

    public OrganizationCacheInvalidator(
            @Qualifier("departmentLoadingCache") LoadingCache<OrganizationCacheKey, java.util.List<DepartmentCacheRow>> departmentCache,
            @Qualifier("teamLoadingCache") LoadingCache<OrganizationCacheKey, java.util.List<TeamCacheRow>> teamCache,
            @Qualifier("teamManagerLoadingCache") LoadingCache<String, java.util.List<TeamManagerCacheRow>> teamManagerCache,
            CacheManager cacheManager,
            AfterCommitExecutor afterCommitExecutor,
            EmployeeViewCacheKey employeeViewCacheKey) {
        this.departmentCache = departmentCache;
        this.teamCache = teamCache;
        this.teamManagerCache = teamManagerCache;
        this.cacheManager = cacheManager;
        this.afterCommitExecutor = afterCommitExecutor;
        this.employeeViewCacheKey = employeeViewCacheKey;
    }

    public void afterEmployeeViewChange() {
        afterCommitExecutor.execute(() -> clearSpringCache(CacheConfig.CACHE_EMPLOYEES));
    }

    // 조직 정보 변경 후 관련 조직 캐시와 직원 응답 캐시를 갱신한다
    public void afterEmployeeOrganizationChange(Collection<Long> managedTeamIds) {
        afterCommitExecutor.execute(() -> {
            if (managedTeamIds != null && !managedTeamIds.isEmpty()) {
                // 원본 조직 snapshot을 먼저 비운 뒤 파생 응답 캐시를 제거한다.
                teamManagerCache.invalidate(CacheConfig.TOTAL_KEY);
                managedTeamIds.stream()
                        .filter(id -> id != null)
                        .map(String::valueOf)
                        .forEach(teamManagerCache::invalidate);
            }

            clearSpringCache(CacheConfig.CACHE_TEAM_MANAGEMENT_DATA);
            invalidateEmployeeViews();
        });
    }

    // 변경된 부서와 전체 부서 목록 캐시를 갱신한다
    public void afterDepartmentChange(Collection<String> departmentNames, boolean evictEmployeeCache) {
        afterCommitExecutor.execute(() -> {
            departmentCache.invalidate(OrganizationCacheKey.allRows());
            departmentNames.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(OrganizationCacheKey::byName)
                    .forEach(departmentCache::invalidate);
            if (evictEmployeeCache) {
                invalidateEmployeeViews();
            }
        });
    }

    // 변경된 팀과 팀 관리 화면 캐시를 갱신한다
    public void afterTeamChange(Collection<String> teamNames, boolean evictEmployeeCache) {
        afterCommitExecutor.execute(() -> {
            teamCache.invalidate(OrganizationCacheKey.allRows());
            teamNames.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(OrganizationCacheKey::byName)
                    .forEach(teamCache::invalidate);
            clearSpringCache(CacheConfig.CACHE_TEAM_MANAGEMENT_DATA);
            if (evictEmployeeCache) {
                invalidateEmployeeViews();
            }
        });
    }

    // 담당자 변경 후 조직과 직원 응답 캐시를 갱신한다
    public void afterTeamManagerChange(Collection<Long> teamIds) {
        afterCommitExecutor.execute(() -> {
            teamManagerCache.invalidate(CacheConfig.TOTAL_KEY);
            teamIds.stream()
                    .filter(id -> id != null)
                    .map(String::valueOf)
                    .forEach(teamManagerCache::invalidate);
            clearSpringCache(CacheConfig.CACHE_TEAM_MANAGEMENT_DATA);
            invalidateEmployeeViews();
        });
    }

    private void invalidateEmployeeViews() {
        employeeViewCacheKey.bumpOrganization();
        clearSpringCache(CacheConfig.CACHE_EMPLOYEES);
    }

    private void clearSpringCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
