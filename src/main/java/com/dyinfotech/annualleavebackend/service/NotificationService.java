package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
//					log.error("신규 FCM token topic 등록 최종 실패. token={}, employeeId={}", fcmToken, employeeId);
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
//		log.info("FCM token 소유자 변경 감지. oldEmployeeId={}, newEmployeeId={}, token={}", oldEmployeeId, employeeId, fcmToken);
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
	
	@Transactional
	public void syncToken(Long employeeId, String fcmToken, String deviceOs) {
		// DB에 토큰이 있으면 소유자 변경 처리
		tokenRepository.findByToken(fcmToken)	// Topic은 migrate(), 토큰 소유자는 updateTokenAndTouch()에서 처리하도록 순서를 맞춤
						.ifPresent(existingToken -> syncExistingToken(existingToken, employeeId, fcmToken));
		
		// DB에 토큰이 없으면 새로운 토큰 생성
		if (tokenRepository.updateTokenAndTouch(employeeId, deviceOs, LocalDateTime.now(clock), fcmToken) == 0) {
			subscribeRetry(fcmToken, employeeId, 1).thenAccept(result -> {
                if (result == TopicSyncResult.SUCCESS) {
                    tokenRepository.save(new FcmToken(employeeId, fcmToken, deviceOs));
                    return;
                }

                log.error("신규 FCM token topic 등록 최종 실패. token={}, employeeId={}", fcmToken, employeeId);
            });
		}
	}
	
	private CompletableFuture<TopicSyncResult> subscribeRetry(String token, Long employeeId, int retryCount) {
	    return subscribeTopic(token, employeeId)
	            .thenCompose(result -> {
	                if (result == TopicSyncResult.SUCCESS || retryCount >= MAX_RETRY_COUNT) {
	                    return CompletableFuture.completedFuture(result);
	                }
	                
	                log.warn("FCM subscribe retry. retry={}/{}, token={}, employeeId={}", retryCount, MAX_RETRY_COUNT, token, employeeId);
	                
	                return delay(100L << (retryCount - 1))
	                		.thenCompose(v ->  subscribeRetry(token, employeeId, retryCount + 1));
	            });
	}

	private void syncExistingToken(FcmToken existingToken, Long employeeId, String fcmToken) {
		Long oldEmployeeId = existingToken.getEmployeeId();
		if (oldEmployeeId.equals(employeeId)) {
			return;
		}

		log.info("FCM token 소유자 변경 감지. oldEmployeeId={}, newEmployeeId={}, token={}", oldEmployeeId, employeeId, fcmToken);
		
		migrate(fcmToken, oldEmployeeId, employeeId, TopicSyncResult.NONE, 1)
        .thenAccept(result -> {
            if (result != TopicSyncResult.SUCCESS) {
                log.error("FCM topic migration 최종 실패. result={}, token={}, oldEmployeeId={}, newEmployeeId={}", result, fcmToken, oldEmployeeId, employeeId);
            }
        });
	}
	
	private CompletableFuture<TopicSyncResult> migrate(String token, Long oldEmployeeId, Long newEmployeeId, TopicSyncResult previousResult, int retryCount) {
		return migrateOnce(token, oldEmployeeId, newEmployeeId, previousResult)
				.thenCompose(result -> {
					if (result == TopicSyncResult.SUCCESS || retryCount >= MAX_RETRY_COUNT) {
						return CompletableFuture.completedFuture(result);
					}
					
					log.warn("FCM topic migration retry. retry={}/{}, result={}, token={}", retryCount, MAX_RETRY_COUNT, result, token);
					
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
    @Transactional
    public void logoutToken(String fcmToken, Long employeeId) {
        if (!fcmService.unsubscribeTopics(fcmToken, employeeId).join()) {
            log.warn("FCM topic unsubscribe 실패. token={}, employeeId={}", fcmToken, employeeId);
            return;
        }

        tokenRepository.deleteByToken(fcmToken);
    }

    /**
     * ③ 알림 발송 공통 메서드
     */
    public void sendNotificationToTeams(Collection<Long> approverIds, String title, String body) {
        fcmService.sendConditionNotification(approverIds, title, body);
    }
}
