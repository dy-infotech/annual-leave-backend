package com.dyinfotech.annualleavebackend.common.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 직원 응답 캐시의 세대 키를 관리한다.
 *
 * 조직 세대가 바뀌면 모든 직원 응답이 새 키 공간으로 이동하고,
 * 직원 세대가 바뀌면 해당 직원만 새 키 공간으로 이동한다.
 * 날짜도 키에 포함해 월차 발생일/PM 재직 경계가 넘어가면 새 snapshot을 사용한다.
 */
@Component
public class EmployeeViewCacheKey {

    private static final Duration VERSION_RETENTION = Duration.ofHours(2);

    private final Clock clock;
    private final AtomicLong organizationVersion = new AtomicLong();
    private final Cache<Long, AtomicLong> employeeVersions = Caffeine.newBuilder()
            // Spring employee 응답 TTL(30분)보다 충분히 길게 유지한 뒤 제거하므로
            // version이 0으로 돌아가도 살아 있는 구세대 cache key와 충돌하지 않는다.
            .expireAfterAccess(VERSION_RETENTION)
            .maximumSize(20_000)
            .build();

    public EmployeeViewCacheKey(Clock clock) {
        this.clock = clock;
    }

    public String key(Long employeeId) {
        long employeeVersion = employeeVersions
                .get(employeeId, ignored -> new AtomicLong())
                .get();
        return LocalDate.now(clock)
                + ":" + organizationVersion.get()
                + ":" + employeeId
                + ":" + employeeVersion;
    }

    public void bumpOrganization() {
        organizationVersion.incrementAndGet();
    }

    public void bumpEmployee(Long employeeId) {
        if (employeeId != null) {
            employeeVersions
                    .get(employeeId, ignored -> new AtomicLong())
                    .incrementAndGet();
        }
    }
}
