package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.common.IpContext;
import com.dyinfotech.annualleavebackend.domain.FcmToken;
import com.dyinfotech.annualleavebackend.repository.FcmTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
	private final FcmTokenRepository tokenRepository;
    private final FcmService fcmService;
    
    private final Clock clock;
	
	private final ScheduledExecutorService retryExecutor;
    
    private static final int MAX_RETRY_COUNT = 3;
    private static final int FCM_CLEANUP_BATCH_SIZE = 20;

    /**
     * 동일 FCM token의 owner sync/logout은 외부 Firebase topic 작업까지 순서를 보장한다.
     * 현재 단일 Backend 인스턴스에서 race를 막고, DB owner CAS는 교차 인스턴스 방어로 유지한다.
     */
    private final ConcurrentHashMap<String, CompletableFuture<Void>> tokenOperations = new ConcurrentHashMap<>();
    
	private enum TopicSyncResult {
		NONE, UNSUBSCRIBE_FAILED, SUBSCRIBE_FAILED, SUCCESS
	}

//	// 동기 방식으로 구현된 syncToken 메서드.
//	@Transactional
//	public void syncToken(Long employeeId, String fcmToken, String deviceOs) {
//		// DB에 토큰이 있으면 소유자 변경 처리
//		tokenRepository.findByToken(fcmToken)
//						.ifPresent(existingToken -> syncExistingToken(existingToken, employeeId, fcmToken));
//		
//		// DB에 토큰이 없으면 새로운 토큰 생성
//		if (tokenRepository.updateTokenAndTouch(employeeId, deviceOs, LocalDateTime.now(clock), fcmToken) == 0) {
//			for (int retryCount = 1; retryCount <= MAX_RETRY_COUNT; ++retryCount) {
//				if (fcmService.subscribeTopics(fcmToken, employeeId).join()) {
//					tokenRepository.save(new FcmToken(employeeId, fcmToken, deviceOs));
//					break;
//				}
//				
//				if (retryCount < MAX_RETRY_COUNT) {
//					log.warn("신규 FCM token topic 등록 실패. retry:{}/{}, token={}, employeeId={}", retryCount, MAX_RETRY_COUNT, fcmToken, employeeId);
//				} else {
//					log.error("신규 FCM token topic 등록 최종 실패. employeeId={}", employeeId);
//				}
//			}
//		}
//	}
//
//	private void syncExistingToken(FcmToken existingToken, Long employeeId, String fcmToken) {
//		Long oldEmployeeId = existingToken.getEmployeeId();
//		if (oldEmployeeId.equals(employeeId)) {
//			return;
//		}
//
//		log.info("FCM token 소유자 변경 감지. oldEmployeeId={}, newEmployeeId={}", oldEmployeeId, employeeId);
//
//		TopicSyncResult result = migrate(fcmToken, oldEmployeeId, employeeId);
//		if (result != TopicSyncResult.SUCCESS) {
//			log.error("FCM topic migration 최종 실패. result={}, token={}, oldEmployeeId={}, newEmployeeId={}", result,
//					fcmToken, oldEmployeeId, employeeId);
//		}
//	}
//
//	public TopicSyncResult migrate(String token, Long oldEmployeeId, Long newEmployeeId) {
//		TopicSyncResult result = TopicSyncResult.NONE;
//		for (int retryCount = 1; retryCount <= MAX_RETRY_COUNT; ++retryCount) {
//			result = migrateOnce(token, oldEmployeeId, newEmployeeId, result);
//			if (result == TopicSyncResult.SUCCESS) {
//				return result;
//			}
//
//			log.warn("FCM topic migration retry. retry={}/{}, result={}, token={}", retryCount, MAX_RETRY_COUNT, result,
//					token);
//		}
//
//		return result;
//	}
//	
//	private TopicSyncResult migrateOnce(String token, Long oldEmployeeId, Long newEmployeeId, TopicSyncResult result) {
//		switch (result) {
//			case NONE:
//			case UNSUBSCRIBE_FAILED:
//				if (!fcmService.unsubscribeTopics(token, oldEmployeeId).join()) {
//					return TopicSyncResult.UNSUBSCRIBE_FAILED;
//				}
//				// fall through
//			case SUBSCRIBE_FAILED:
//				if (!fcmService.subscribeTopics(token, newEmployeeId).join()) {
//					return TopicSyncResult.SUBSCRIBE_FAILED;
//				}
//				return TopicSyncResult.SUCCESS;
//			case SUCCESS:
//				return TopicSyncResult.SUCCESS;
//			default:
//				throw new IllegalStateException("Unexpected result: " + result);
//		}
//	}
	
	private CompletableFuture<Void> delay(long millis) {
	    CompletableFuture<Void> future = new CompletableFuture<>();

	    retryExecutor.schedule(() -> future.complete(null), millis, TimeUnit.MILLISECONDS);

	    return future;
	}
	
	public CompletableFuture<Void> syncToken(Long employeeId, String fcmToken, String deviceOs) {
        String clientIp = IpContext.get();
        return serializeTokenOperation(
                fcmToken,
                () -> syncTokenNow(employeeId, fcmToken, deviceOs, clientIp));
    }

    private CompletableFuture<Void> syncTokenNow(
            Long employeeId,
            String fcmToken,
            String deviceOs,
            String clientIp) {
		LocalDateTime now = LocalDateTime.now(clock);
		FcmToken existingToken = tokenRepository.findByToken(fcmToken).orElse(null);
		if (existingToken != null) {
			return syncExistingToken(existingToken, employeeId, fcmToken)
					.thenCompose(result -> {
						if (result != TopicSyncResult.SUCCESS) {
							log.error("FCM topic migration 최종 실패. result={}, oldEmployeeId={}, newEmployeeId={}",
									result, existingToken.getEmployeeId(), employeeId);
							return CompletableFuture.failedFuture(
									new IllegalStateException("FCM topic migration 실패"));
						}

						final int[] changed = new int[1];
						runWithIpContext(clientIp, () -> changed[0] = tokenRepository.updateTokenAndTouchIfOwner(
								existingToken.getEmployeeId(), employeeId, deviceOs, now, fcmToken));
						if (changed[0] == 1) {
							return CompletableFuture.completedFuture(null);
						}

						return reconcileTopicsToCurrentOwner(fcmToken, employeeId)
								.thenCompose(ignored -> CompletableFuture.failedFuture(
										new IllegalStateException("FCM token 소유자가 동시에 변경되었습니다.")));
					});
		}
		
		// DB에 토큰이 없으면 새로운 토큰 생성
		return subscribeRetry(fcmToken, employeeId, 1)
				.thenCompose(result -> {
					if (result != TopicSyncResult.SUCCESS) {
						log.error("신규 FCM token topic 등록 최종 실패. employeeId={}", employeeId);
						return CompletableFuture.failedFuture(
								new IllegalStateException("FCM topic 등록 실패"));
					}

					try {
						runWithIpContext(clientIp, () -> tokenRepository.saveAndFlush(
								new FcmToken(employeeId, fcmToken, deviceOs)));
						return CompletableFuture.completedFuture(null);
					} catch (RuntimeException e) {
						return reconcileTopicsToCurrentOwner(fcmToken, employeeId)
								.thenCompose(ignored -> CompletableFuture.failedFuture(e));
					}
				});
	}

	private CompletableFuture<Void> reconcileTopicsToCurrentOwner(
			String fcmToken,
			Long attemptedEmployeeId) {
		final Long currentOwnerId;
		try {
			currentOwnerId = tokenRepository.findByToken(fcmToken)
					.map(FcmToken::getEmployeeId)
					.orElse(null);
		} catch (RuntimeException e) {
			// DB 정본을 확인하지 못한 상태에서 unsubscribe하면 정상 owner의 topic까지
			// 지울 수 있으므로 외부 상태를 임의 변경하지 않는다.
			log.error("FCM owner 경합 후 현재 owner 확인 실패. topic reconciliation을 건너뜁니다.", e);
			return CompletableFuture.completedFuture(null);
		}

		if (Objects.equals(currentOwnerId, attemptedEmployeeId)) {
			// 다른 인스턴스가 동일 owner로 DB 반영을 먼저 끝낸 경우 현재 topic이 정답이다.
			return CompletableFuture.completedFuture(null);
		}

		CompletableFuture<Void> removeAttempted = fcmService
				.unsubscribeTopics(fcmToken, attemptedEmployeeId)
				.thenAccept(success -> {
					if (!success) {
						log.error("FCM 경합 보상 중 잘못된 owner topic 해제 실패. employeeId={}",
								attemptedEmployeeId);
					}
				});

		if (currentOwnerId == null) {
			return removeAttempted;
		}

		return removeAttempted.thenCompose(ignored ->
				fcmService.subscribeTopics(fcmToken, currentOwnerId)
						.thenAccept(success -> {
							if (!success) {
								log.error("FCM 경합 보상 중 현재 owner topic 복구 실패. employeeId={}",
										currentOwnerId);
							}
						}));
	}

	private void runWithIpContext(String clientIp, Runnable action) {
		String previousIp = IpContext.get();
		try {
			IpContext.set(clientIp);
			action.run();
		} finally {
			if ("SYSTEM".equals(previousIp)) {
				IpContext.clear();
			} else {
				IpContext.set(previousIp);
			}
		}
	}

	private CompletableFuture<TopicSyncResult> subscribeRetry(String token, Long employeeId, int retryCount) {
	    return subscribeTopic(token, employeeId)
	            .thenCompose(result -> {
	                if (result == TopicSyncResult.SUCCESS || retryCount >= MAX_RETRY_COUNT) {
	                    return CompletableFuture.completedFuture(result);
	                }
	                
	                log.warn("FCM subscribe retry. retry={}/{}, employeeId={}", retryCount, MAX_RETRY_COUNT, employeeId);
	                
	                return delay(100L << (retryCount - 1))
	                		.thenCompose(v ->  subscribeRetry(token, employeeId, retryCount + 1));
	            });
	}

	private CompletableFuture<TopicSyncResult> syncExistingToken(FcmToken existingToken, Long employeeId, String fcmToken) {
		Long oldEmployeeId = existingToken.getEmployeeId();
		if (oldEmployeeId.equals(employeeId)) {
			return CompletableFuture.completedFuture(TopicSyncResult.SUCCESS);
		}

		log.info("FCM token 소유자 변경 감지. oldEmployeeId={}, newEmployeeId={}", oldEmployeeId, employeeId);
		return migrate(fcmToken, oldEmployeeId, employeeId, TopicSyncResult.NONE, 1);
	}
	
	private CompletableFuture<TopicSyncResult> migrate(String token, Long oldEmployeeId, Long newEmployeeId, TopicSyncResult previousResult, int retryCount) {
		return migrateOnce(token, oldEmployeeId, newEmployeeId, previousResult)
				.thenCompose(result -> {
					if (result == TopicSyncResult.SUCCESS || retryCount >= MAX_RETRY_COUNT) {
						return CompletableFuture.completedFuture(result);
					}
					
					log.warn("FCM topic migration retry. retry={}/{}, result={}", retryCount, MAX_RETRY_COUNT, result);
					
					return delay(100L << (retryCount - 1))
							.thenCompose(v -> migrate(token, oldEmployeeId, newEmployeeId, result, retryCount + 1));
				});
	}
	
	private CompletableFuture<TopicSyncResult> migrateOnce(String token, Long oldEmployeeId, Long newEmployeeId, TopicSyncResult result) {
		switch (result) {
			case NONE:
			case UNSUBSCRIBE_FAILED:
				return fcmService.unsubscribeTopics(token, oldEmployeeId)
			                    .thenCompose(unsubscribeSuccess -> {
			                        if (!unsubscribeSuccess) {
			                            return CompletableFuture.completedFuture(TopicSyncResult.UNSUBSCRIBE_FAILED);
			                        }
			                        
			                        return subscribeTopic(token, newEmployeeId);
			                    });
			case SUBSCRIBE_FAILED:
				return subscribeTopic(token, newEmployeeId);
			case SUCCESS:
				return CompletableFuture.completedFuture(TopicSyncResult.SUCCESS);
			default:
				throw new IllegalStateException("Unexpected result: " + result);
		}
	}
	
	private CompletableFuture<TopicSyncResult> subscribeTopic(String token, Long employeeId) {
	    return fcmService.subscribeTopics(token, employeeId)
				            .thenApply(success -> success ? TopicSyncResult.SUCCESS : TopicSyncResult.SUBSCRIBE_FAILED);
	}

    /**
     * ② 로그아웃 및 기기 해제
     * TODO: 로그아웃 기능 구현 및 토큰 삭제 적용
     */
    public CompletableFuture<Void> logoutToken(String fcmToken, Long employeeId) {
        return serializeTokenOperation(
                fcmToken,
                () -> logoutTokenNow(fcmToken, employeeId));
    }

    private CompletableFuture<Void> logoutTokenNow(String fcmToken, Long employeeId) {
        FcmToken existing = tokenRepository.findByToken(fcmToken).orElse(null);
        if (existing == null) {
            return CompletableFuture.completedFuture(null);
        }

        Long topicOwnerId = existing.getEmployeeId();
        if (!topicOwnerId.equals(employeeId)) {
            // 늦게 도착한 이전 계정의 로그아웃은 새 소유자의 topic/DB binding을 건드리지 않는다.
            log.info("FCM stale logout ignored. requestEmployeeId={}, currentOwnerId={}",
                    employeeId, topicOwnerId);
            return CompletableFuture.completedFuture(null);
        }

        return fcmService.unsubscribeTopics(fcmToken, employeeId)
                .thenAccept(success -> {
                    if (!success) {
                        log.warn("FCM topic unsubscribe 실패. employeeId={}", employeeId);
                        throw new IllegalStateException("FCM topic unsubscribe 실패");
                    }

                    long deleted = tokenRepository.deleteByTokenAndEmployeeId(fcmToken, employeeId);
                    if (deleted == 0) {
                        log.info("FCM logout delete skipped because owner changed concurrently. requestEmployeeId={}",
                                employeeId);
                    }
                });
    }

    private CompletableFuture<Void> serializeTokenOperation(
            String fcmToken,
            Supplier<CompletableFuture<Void>> action) {
        AtomicReference<CompletableFuture<Void>> queuedRef = new AtomicReference<>();

        tokenOperations.compute(fcmToken, (token, previous) -> {
            CompletableFuture<Void> predecessor = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((ignored, error) -> null);
            CompletableFuture<Void> queued = predecessor.thenCompose(ignored -> action.get());
            queuedRef.set(queued);
            return queued;
        });

        CompletableFuture<Void> queued = queuedRef.get();
        queued.whenComplete((ignored, error) -> tokenOperations.remove(fcmToken, queued));
        return queued;
    }

    public void cleanupInactiveTokens(LocalDateTime now, int monthCount) {
        LocalDateTime cutoff = now.minusMonths(monthCount);
        Long cursorTokenId = null;

        while (true) {
            var batch = tokenRepository.findInactiveTokensBatch(
                    cutoff,
                    cursorTokenId,
                    FCM_CLEANUP_BATCH_SIZE);
            if (batch.isEmpty()) {
                return;
            }

            cursorTokenId = batch.get(batch.size() - 1).getTokenId();

            CompletableFuture<?>[] operations = batch.stream()
                    .map(token -> serializeTokenOperation(
                            token.getToken(),
                            () -> cleanupInactiveTokenNow(
                                    token.getToken(),
                                    token.getEmployeeId(),
                                    cutoff))
                            .exceptionally(error -> {
                                log.warn(
                                        "FCM inactive cleanup 개별 작업 실패. tokenId={}, employeeId={}",
                                        token.getTokenId(),
                                        token.getEmployeeId(),
                                        error);
                                return null;
                            }))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(operations).join();

            if (batch.size() < FCM_CLEANUP_BATCH_SIZE) {
                return;
            }
        }
    }

    private CompletableFuture<Void> cleanupInactiveTokenNow(
            String fcmToken,
            Long expectedEmployeeId,
            LocalDateTime cutoff) {
        FcmToken current = tokenRepository.findByToken(fcmToken).orElse(null);
        if (current == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (!java.util.Objects.equals(current.getEmployeeId(), expectedEmployeeId)) {
            log.info(
                    "FCM inactive cleanup skipped because owner changed. expectedEmployeeId={}, currentOwnerId={}",
                    expectedEmployeeId,
                    current.getEmployeeId());
            return CompletableFuture.completedFuture(null);
        }

        LocalDateTime updatedAt = current.getUpdatedAudit().getUpdatedAt();
        if (updatedAt == null || !updatedAt.isBefore(cutoff)) {
            log.info(
                    "FCM inactive cleanup skipped because token was refreshed. employeeId={}, updatedAt={}",
                    expectedEmployeeId,
                    updatedAt);
            return CompletableFuture.completedFuture(null);
        }

        return fcmService.unsubscribeTopics(fcmToken, expectedEmployeeId)
                .thenAccept(success -> {
                    if (!success) {
                        log.warn("비활성 FCM token topic 해제 실패. employeeId={}", expectedEmployeeId);
                        return;
                    }

                    long deleted = tokenRepository.deleteByTokenAndEmployeeId(
                            fcmToken,
                            expectedEmployeeId);
                    if (deleted == 0) {
                        log.info(
                                "FCM inactive cleanup delete skipped because owner changed concurrently. employeeId={}",
                                expectedEmployeeId);
                    }
                });
    }

    /**
     * ③ 알림 발송 공통 메서드
     */
    public void sendNotificationToTeams(Collection<Long> approverIds, String title, String body) {
        try {
            fcmService.sendConditionNotification(approverIds, title, body);
        } catch (TaskRejectedException e) {
            // DB commit은 이미 완료됐다. 알림 executor 포화가 API 성공을 실패로 뒤집지 않게 격리한다.
            log.warn("FCM executor 포화로 fallback 발송을 예약합니다. approverCount={}",
                    approverIds != null ? approverIds.size() : 0, e);
            scheduleNotificationFallback(approverIds, title, body, 1);
        } catch (RuntimeException e) {
            // afterCommit callback에서 예외가 요청 스레드로 전파되지 않도록 방어한다.
            log.error("FCM 비동기 작업 제출 실패. fallback 발송을 예약합니다.", e);
            scheduleNotificationFallback(approverIds, title, body, 1);
        }
    }

    private void scheduleNotificationFallback(
            Collection<Long> approverIds,
            String title,
            String body,
            int retryCount) {
        long delayMillis = retryCount <= 1 ? 0L : 100L << (retryCount - 2);
        try {
            retryExecutor.schedule(() -> {
                try {
                    fcmService.sendConditionNotificationNow(approverIds, title, body);
                } catch (RuntimeException e) {
                    if (retryCount < MAX_RETRY_COUNT) {
                        log.warn("FCM fallback 발송 재시도. retry={}/{}", retryCount, MAX_RETRY_COUNT, e);
                        scheduleNotificationFallback(
                                approverIds,
                                title,
                                body,
                                retryCount + 1);
                    } else {
                        log.error("FCM fallback 발송 최종 실패. retry={}/{}", retryCount, MAX_RETRY_COUNT, e);
                    }
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            // retry executor까지 종료/포화된 경우에도 이미 커밋된 비즈니스 결과는 성공으로 유지한다.
            log.error("FCM fallback 작업 예약 실패. 알림은 유실될 수 있습니다.", e);
        }
    }
}
