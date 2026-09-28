package com.dyinfotech.annualleavebackend.common.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * 직원 응답 캐시의 세대 키를 관리한다.
 *
 * 조직 세대가 바뀌면 모든 직원 응답이 새 키 공간으로 이동하고,
 * 직원 세대가 바뀌면 해당 직원만 새 키 공간으로 이동한다.
 * 무효화 시점에 이미 진행 중이던 조회가 뒤늦게 put 하더라도 구세대 키에만 저장된다.
 */
@Component
public class EmployeeViewCacheKey {

    private final AtomicLong organizationVersion = new AtomicLong();
    private final ConcurrentHashMap<Long, AtomicLong> employeeVersions = new ConcurrentHashMap<>();

    public String key(Long employeeId) {
        long employeeVersion = employeeVersions
                .computeIfAbsent(employeeId, ignored -> new AtomicLong())
                .get();
        return organizationVersion.get() + ":" + employeeId + ":" + employeeVersion;
    }

    public void bumpOrganization() {
        organizationVersion.incrementAndGet();
    }

    public void bumpEmployee(Long employeeId) {
        if (employeeId != null) {
            employeeVersions.computeIfAbsent(employeeId, ignored -> new AtomicLong()).incrementAndGet();
        }
    }
}
