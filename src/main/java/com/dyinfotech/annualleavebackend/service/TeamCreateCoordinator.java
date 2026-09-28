package com.dyinfotech.annualleavebackend.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.dto.TeamDto;

import lombok.RequiredArgsConstructor;

/**
 * 같은 Idempotency-Key의 동시 팀 생성 요청을 단일 JVM 안에서 직렬화한다.
 * 실제 처리 결과는 TEAM.create_request_key/create_request_hash에 영속되므로
 * 프로세스 재시작 뒤 재요청도 TeamService가 동일 결과로 복원한다.
 */
@Component
@RequiredArgsConstructor
public class TeamCreateCoordinator {

    private final TeamService teamService;
    private final ConcurrentMap<String, LockEntry> requestLocks = new ConcurrentHashMap<>();

    public Long createTeam(
            Long requesterId,
            TeamDto.CreateRequest request,
            String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return teamService.createTeam(requesterId, request, null);
        }

        String lockKey = idempotencyKey.trim();
        LockEntry entry = requestLocks.compute(lockKey, (key, current) -> {
            LockEntry resolved = current != null ? current : new LockEntry();
            resolved.references++;
            return resolved;
        });

        try {
            synchronized (entry.monitor) {
                return teamService.createTeam(requesterId, request, lockKey);
            }
        } finally {
            requestLocks.computeIfPresent(lockKey, (key, current) -> {
                if (current != entry) {
                    return current;
                }
                current.references--;
                return current.references == 0 ? null : current;
            });
        }
    }

    private static final class LockEntry {
        private final Object monitor = new Object();
        private int references;
    }
}
