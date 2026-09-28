package com.dyinfotech.annualleavebackend.common.cache;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.common.transaction.AfterCommitExecutor;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamManagerCacheRow;
import com.github.benmanes.caffeine.cache.LoadingCache;

@Component
public class OrganizationCacheInvalidator {

    private final LoadingCache<String, java.util.List<DepartmentCacheRow>> departmentCache;
    private final LoadingCache<String, java.util.List<TeamCacheRow>> teamCache;
    private final LoadingCache<String, java.util.List<TeamManagerCacheRow>> teamManagerCache;
    private final CacheManager cacheManager;
    private final AfterCommitExecutor afterCommitExecutor;

    public OrganizationCacheInvalidator(
            @Qualifier("departmentLoadingCache") LoadingCache<String, java.util.List<DepartmentCacheRow>> departmentCache,
            @Qualifier("teamLoadingCache") LoadingCache<String, java.util.List<TeamCacheRow>> teamCache,
            @Qualifier("teamManagerLoadingCache") LoadingCache<String, java.util.List<TeamManagerCacheRow>> teamManagerCache,
            CacheManager cacheManager,
            AfterCommitExecutor afterCommitExecutor) {
        this.departmentCache = departmentCache;
        this.teamCache = teamCache;
        this.teamManagerCache = teamManagerCache;
        this.cacheManager = cacheManager;
        this.afterCommitExecutor = afterCommitExecutor;
    }

    public void afterEmployeeViewChange() {
        afterCommitExecutor.execute(() -> clearSpringCache(CacheConfig.CACHE_EMPLOYEES));
    }

    public void afterEmployeeOrganizationChange(Collection<Long> managedTeamIds) {
        afterCommitExecutor.execute(() -> {
            clearSpringCache(CacheConfig.CACHE_EMPLOYEES);
            clearSpringCache(CacheConfig.CACHE_TEAM_MANAGEMENT_DATA);

            if (managedTeamIds == null || managedTeamIds.isEmpty()) {
                return;
            }

            teamManagerCache.invalidate(CacheConfig.TOTAL_KEY);
            managedTeamIds.stream()
                    .filter(id -> id != null)
                    .map(String::valueOf)
                    .forEach(teamManagerCache::invalidate);
        });
    }

    public void afterDepartmentChange(Collection<String> departmentNames, boolean evictEmployeeCache) {
        afterCommitExecutor.execute(() -> {
            departmentCache.invalidate(CacheConfig.TOTAL_KEY);
            departmentNames.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .forEach(departmentCache::invalidate);
            if (evictEmployeeCache) {
                clearSpringCache(CacheConfig.CACHE_EMPLOYEES);
            }
        });
    }

    public void afterTeamChange(Collection<String> teamNames, boolean evictEmployeeCache) {
        afterCommitExecutor.execute(() -> {
            teamCache.invalidate(CacheConfig.TOTAL_KEY);
            teamNames.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .forEach(teamCache::invalidate);
            clearSpringCache(CacheConfig.CACHE_TEAM_MANAGEMENT_DATA);
            if (evictEmployeeCache) {
                clearSpringCache(CacheConfig.CACHE_EMPLOYEES);
            }
        });
    }

    public void afterTeamManagerChange(Collection<Long> teamIds) {
        afterCommitExecutor.execute(() -> {
            teamManagerCache.invalidate(CacheConfig.TOTAL_KEY);
            teamIds.stream()
                    .filter(id -> id != null)
                    .map(String::valueOf)
                    .forEach(teamManagerCache::invalidate);
            clearSpringCache(CacheConfig.CACHE_TEAM_MANAGEMENT_DATA);
            clearSpringCache(CacheConfig.CACHE_EMPLOYEES);
        });
    }

    private void clearSpringCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
