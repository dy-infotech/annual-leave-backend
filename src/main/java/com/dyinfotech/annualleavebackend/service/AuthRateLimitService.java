package com.dyinfotech.annualleavebackend.service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.IpContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 공개 인증 API의 짧은 구간 abuse를 완화하는 인스턴스 로컬 rate limiter.
 * 장기적으로 RTR/session 저장소가 도입되면 중앙 저장소 기반 제한으로 교체 가능하다.
 */
@Service
public class AuthRateLimitService {

    private static final int SIGN_IN_IDENTITY_LIMIT = 10;
    private static final int SIGN_IN_IP_LIMIT = 30;
    private static final int RECOVERY_IDENTITY_LIMIT = 3;
    private static final int RECOVERY_IP_LIMIT = 10;

    private final Cache<String, AtomicInteger> signInIdentity = counterCache(Duration.ofMinutes(1));
    private final Cache<String, AtomicInteger> signInIp = counterCache(Duration.ofMinutes(1));
    private final Cache<String, AtomicInteger> recoveryIdentity = counterCache(Duration.ofMinutes(10));
    private final Cache<String, AtomicInteger> recoveryIp = counterCache(Duration.ofMinutes(10));

    private static Cache<String, AtomicInteger> counterCache(Duration duration) {
        return Caffeine.newBuilder()
                .expireAfterWrite(duration)
                .maximumSize(20_000)
                .build();
    }

    public void checkSignIn(String employeeNumber) {
        String ip = IpContext.get();
        if (isInternalContext(ip)) {
            return;
        }
        String identity = normalize(employeeNumber);
        acquire(signInIdentity, ip + "|" + identity, SIGN_IN_IDENTITY_LIMIT,
                "로그인 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        acquire(signInIp, ip, SIGN_IN_IP_LIMIT,
                "로그인 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
    }

    public void clearSignIn(String employeeNumber) {
        String ip = IpContext.get();
        if (!isInternalContext(ip)) {
            signInIdentity.invalidate(ip + "|" + normalize(employeeNumber));
        }
    }

    public void checkRecovery(String identity) {
        String ip = IpContext.get();
        if (isInternalContext(ip)) {
            return;
        }
        String normalized = normalize(identity);
        acquire(recoveryIdentity, normalized, RECOVERY_IDENTITY_LIMIT,
                "계정 복구 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        acquire(recoveryIp, ip, RECOVERY_IP_LIMIT,
                "계정 복구 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
    }

    private void acquire(Cache<String, AtomicInteger> cache, String key, int limit, String message) {
        AtomicInteger counter = cache.asMap().computeIfAbsent(key, ignored -> new AtomicInteger());
        if (counter.incrementAndGet() > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    private boolean isInternalContext(String ip) {
        return ip == null || ip.isBlank() || "SYSTEM".equals(ip);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
