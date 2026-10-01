package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;

import com.dyinfotech.annualleavebackend.common.IpContext;
import com.dyinfotech.annualleavebackend.domain.FcmToken;
import com.dyinfotech.annualleavebackend.domain.support.UpdatedAudit;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.FcmTokenRepository;

class NotificationFcmRegressionTest {

    private static final String TOKEN = "test-fcm-token";
    private static final String DEVICE_OS = "Web";
    private static final Long OLD_EMPLOYEE_ID = 1L;
    private static final Long NEW_EMPLOYEE_ID = 2L;

    private FcmTokenRepository tokenRepository;
    private FcmService fcmService;
    private ScheduledExecutorService retryExecutor;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(FcmTokenRepository.class);
        fcmService = mock(FcmService.class);
        retryExecutor = Executors.newSingleThreadScheduledExecutor();

        Clock clock = Clock.fixed(
                Instant.parse("2026-09-18T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        notificationService = new NotificationService(
                tokenRepository,
                mock(EmployeeRepository.class),
                mock(TeamService.class),
                fcmService,
                clock,
                retryExecutor
        );
    }

    @AfterEach
    void tearDown() {
        IpContext.clear();
        retryExecutor.shutdownNow();
    }

    @Test
    void migration_subscribeFailure_retriesSubscribeOnly() {
        FcmToken existingToken = mock(FcmToken.class);
        when(existingToken.getEmployeeId()).thenReturn(OLD_EMPLOYEE_ID);
        when(tokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(existingToken));
        when(fcmService.unsubscribeTopics(TOKEN, OLD_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(fcmService.subscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(
                        CompletableFuture.completedFuture(false),
                        CompletableFuture.completedFuture(true)
                );
        when(tokenRepository.updateTokenAndTouchIfOwner(
                eq(OLD_EMPLOYEE_ID),
                eq(NEW_EMPLOYEE_ID),
                eq(DEVICE_OS),
                any(LocalDateTime.class),
                eq(TOKEN)
        )).thenReturn(1);

        notificationService.syncToken(NEW_EMPLOYEE_ID, TOKEN, DEVICE_OS).join();

        verify(fcmService, times(1)).unsubscribeTopics(TOKEN, OLD_EMPLOYEE_ID);
        verify(fcmService, times(2)).subscribeTopics(TOKEN, NEW_EMPLOYEE_ID);
        verify(tokenRepository, times(1)).updateTokenAndTouchIfOwner(
                eq(OLD_EMPLOYEE_ID),
                eq(NEW_EMPLOYEE_ID),
                eq(DEVICE_OS),
                any(LocalDateTime.class),
                eq(TOKEN)
        );
    }

    @Test
    void migration_unsubscribeFailure_retriesFromUnsubscribe() {
        FcmToken existingToken = mock(FcmToken.class);
        when(existingToken.getEmployeeId()).thenReturn(OLD_EMPLOYEE_ID);
        when(tokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(existingToken));
        when(fcmService.unsubscribeTopics(TOKEN, OLD_EMPLOYEE_ID))
                .thenReturn(
                        CompletableFuture.completedFuture(false),
                        CompletableFuture.completedFuture(true)
                );
        when(fcmService.subscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(tokenRepository.updateTokenAndTouchIfOwner(
                eq(OLD_EMPLOYEE_ID),
                eq(NEW_EMPLOYEE_ID),
                eq(DEVICE_OS),
                any(LocalDateTime.class),
                eq(TOKEN)
        )).thenReturn(1);

        notificationService.syncToken(NEW_EMPLOYEE_ID, TOKEN, DEVICE_OS).join();

        verify(fcmService, times(2)).unsubscribeTopics(TOKEN, OLD_EMPLOYEE_ID);
        verify(fcmService, times(1)).subscribeTopics(TOKEN, NEW_EMPLOYEE_ID);
        verify(tokenRepository, times(1)).updateTokenAndTouchIfOwner(
                eq(OLD_EMPLOYEE_ID),
                eq(NEW_EMPLOYEE_ID),
                eq(DEVICE_OS),
                any(LocalDateTime.class),
                eq(TOKEN)
        );
    }

    @Test
    void newToken_subscribeFailure_doesNotSaveToken() {
        when(tokenRepository.findByToken(TOKEN)).thenReturn(Optional.empty());
        when(fcmService.subscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(false));

        assertThrows(
                CompletionException.class,
                () -> notificationService.syncToken(NEW_EMPLOYEE_ID, TOKEN, DEVICE_OS).join()
        );

        verify(fcmService, times(3)).subscribeTopics(TOKEN, NEW_EMPLOYEE_ID);
        verify(tokenRepository, never()).saveAndFlush(any(FcmToken.class));
    }

    @Test
    void migration_ownerCasLost_reconcilesTopicsToCurrentDbOwner() {
        Long winnerEmployeeId = 3L;
        FcmToken initial = mock(FcmToken.class);
        FcmToken winner = mock(FcmToken.class);
        when(initial.getEmployeeId()).thenReturn(OLD_EMPLOYEE_ID);
        when(winner.getEmployeeId()).thenReturn(winnerEmployeeId);
        when(tokenRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(initial), Optional.of(winner));
        when(fcmService.unsubscribeTopics(TOKEN, OLD_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(fcmService.subscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(tokenRepository.updateTokenAndTouchIfOwner(
                eq(OLD_EMPLOYEE_ID),
                eq(NEW_EMPLOYEE_ID),
                eq(DEVICE_OS),
                any(LocalDateTime.class),
                eq(TOKEN)
        )).thenReturn(0);
        when(fcmService.unsubscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(fcmService.subscribeTopics(TOKEN, winnerEmployeeId))
                .thenReturn(CompletableFuture.completedFuture(true));

        assertThrows(
                CompletionException.class,
                () -> notificationService.syncToken(NEW_EMPLOYEE_ID, TOKEN, DEVICE_OS).join()
        );

        verify(fcmService).unsubscribeTopics(TOKEN, NEW_EMPLOYEE_ID);
        verify(fcmService).subscribeTopics(TOKEN, winnerEmployeeId);
    }

    @Test
    void newToken_insertRace_reconcilesTopicsToWinner() {
        Long winnerEmployeeId = 3L;
        FcmToken winner = mock(FcmToken.class);
        when(winner.getEmployeeId()).thenReturn(winnerEmployeeId);
        when(tokenRepository.findByToken(TOKEN))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(fcmService.subscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(true));
        doThrow(new DataIntegrityViolationException("duplicate token"))
                .when(tokenRepository)
                .saveAndFlush(any(FcmToken.class));
        when(fcmService.unsubscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(fcmService.subscribeTopics(TOKEN, winnerEmployeeId))
                .thenReturn(CompletableFuture.completedFuture(true));

        assertThrows(
                CompletionException.class,
                () -> notificationService.syncToken(NEW_EMPLOYEE_ID, TOKEN, DEVICE_OS).join()
        );

        verify(fcmService).unsubscribeTopics(TOKEN, NEW_EMPLOYEE_ID);
        verify(fcmService).subscribeTopics(TOKEN, winnerEmployeeId);
    }

    @Test
    void asyncDatabaseWrite_preservesRequestIpContext() {
        FcmToken existingToken = mock(FcmToken.class);
        when(existingToken.getEmployeeId()).thenReturn(NEW_EMPLOYEE_ID);
        when(tokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(existingToken));

        IpContext.set("127.0.0.1");
        doAnswer(invocation -> {
            assertEquals("127.0.0.1", IpContext.get());
            return 1;
        }).when(tokenRepository).updateTokenAndTouchIfOwner(
                eq(NEW_EMPLOYEE_ID),
                eq(NEW_EMPLOYEE_ID),
                eq(DEVICE_OS),
                any(LocalDateTime.class),
                eq(TOKEN)
        );

        notificationService.syncToken(NEW_EMPLOYEE_ID, TOKEN, DEVICE_OS).join();

        assertEquals("127.0.0.1", IpContext.get());
    }
    @Test
    void sameOwnerNewSession_updatesFcmSessionBinding() {
        FcmToken existingToken = mock(FcmToken.class);
        when(existingToken.getEmployeeId()).thenReturn(NEW_EMPLOYEE_ID);
        when(existingToken.getAuthSessionMarker()).thenReturn("session-old");
        when(tokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(existingToken));
        when(tokenRepository.updateTokenAndTouchIfBinding(
                eq(NEW_EMPLOYEE_ID),
                eq("session-old"),
                eq(NEW_EMPLOYEE_ID),
                eq("session-new"),
                eq(DEVICE_OS),
                any(LocalDateTime.class),
                eq(TOKEN)
        )).thenReturn(1);

        notificationService.syncToken(
                NEW_EMPLOYEE_ID,
                TOKEN,
                DEVICE_OS,
                "session-new").join();

        verify(tokenRepository).updateTokenAndTouchIfBinding(
                eq(NEW_EMPLOYEE_ID),
                eq("session-old"),
                eq(NEW_EMPLOYEE_ID),
                eq("session-new"),
                eq(DEVICE_OS),
                any(LocalDateTime.class),
                eq(TOKEN));
        verify(fcmService, never()).unsubscribeTopics(TOKEN, NEW_EMPLOYEE_ID);
    }

    @Test
    void staleSessionLogout_doesNotDeleteNewSessionFcmBinding() {
        FcmToken existingToken = mock(FcmToken.class);
        when(existingToken.getEmployeeId()).thenReturn(NEW_EMPLOYEE_ID);
        when(existingToken.getAuthSessionMarker()).thenReturn("session-new");
        when(tokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(existingToken));

        notificationService.logoutToken(
                TOKEN,
                NEW_EMPLOYEE_ID,
                "session-old").join();

        verify(fcmService, never()).unsubscribeTopics(TOKEN, NEW_EMPLOYEE_ID);
        verify(tokenRepository, never()).deleteByTokenAndBinding(
                TOKEN,
                NEW_EMPLOYEE_ID,
                "session-old");
        verify(tokenRepository, never()).deleteByTokenAndEmployeeId(
                TOKEN,
                NEW_EMPLOYEE_ID);
    }

    @Test
    void logout_deleteRace_restoresCurrentSessionTopicBinding() {
        FcmToken initial = mock(FcmToken.class);
        FcmToken rebound = mock(FcmToken.class);
        when(initial.getEmployeeId()).thenReturn(NEW_EMPLOYEE_ID);
        when(initial.getAuthSessionMarker()).thenReturn("session-old");
        when(rebound.getEmployeeId()).thenReturn(NEW_EMPLOYEE_ID);
        when(rebound.getAuthSessionMarker()).thenReturn("session-new");
        when(tokenRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(initial), Optional.of(rebound));
        when(fcmService.unsubscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(tokenRepository.deleteByTokenAndBinding(
                TOKEN,
                NEW_EMPLOYEE_ID,
                "session-old")).thenReturn(0L);
        when(fcmService.subscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(true));

        notificationService.logoutToken(
                TOKEN,
                NEW_EMPLOYEE_ID,
                "session-old").join();

        verify(tokenRepository).deleteByTokenAndBinding(
                TOKEN,
                NEW_EMPLOYEE_ID,
                "session-old");
        verify(fcmService).subscribeTopics(TOKEN, NEW_EMPLOYEE_ID);
    }

    @Test
    void logout_deletesTokenOnlyAfterTopicUnsubscribeSucceeds() {
        CompletableFuture<Boolean> unsubscribeFuture = new CompletableFuture<>();
        FcmToken existingToken = mock(FcmToken.class);
        when(existingToken.getEmployeeId()).thenReturn(NEW_EMPLOYEE_ID);
        when(tokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(existingToken));
        when(fcmService.unsubscribeTopics(TOKEN, NEW_EMPLOYEE_ID)).thenReturn(unsubscribeFuture);
        when(tokenRepository.deleteByTokenAndEmployeeId(TOKEN, NEW_EMPLOYEE_ID)).thenReturn(1L);

        CompletableFuture<Void> result = notificationService.logoutToken(TOKEN, NEW_EMPLOYEE_ID);

        verify(tokenRepository, never()).deleteByTokenAndEmployeeId(TOKEN, NEW_EMPLOYEE_ID);

        unsubscribeFuture.complete(true);
        result.join();

        verify(tokenRepository, times(1)).deleteByTokenAndEmployeeId(TOKEN, NEW_EMPLOYEE_ID);
    }

    @Test
    void logout_unsubscribeFailure_doesNotDeleteToken() {
        FcmToken existingToken = mock(FcmToken.class);
        when(existingToken.getEmployeeId()).thenReturn(NEW_EMPLOYEE_ID);
        when(tokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(existingToken));
        when(fcmService.unsubscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(false));

        assertThrows(
                CompletionException.class,
                () -> notificationService.logoutToken(TOKEN, NEW_EMPLOYEE_ID).join()
        );

        verify(tokenRepository, never()).deleteByTokenAndEmployeeId(TOKEN, NEW_EMPLOYEE_ID);
    }

    @Test
    void notificationQueueRejection_doesNotEscapeAndFallsBack() throws Exception {
        org.mockito.Mockito.doThrow(new TaskRejectedException("queue full"))
                .when(fcmService)
                .sendConditionNotification(
                        org.mockito.ArgumentMatchers.anyCollection(),
                        eq("title"),
                        eq("body"));

        notificationService.sendNotificationToTeams(
                java.util.List.of(NEW_EMPLOYEE_ID),
                "title",
                "body");

        Thread.sleep(100L);

        verify(fcmService, times(1)).sendConditionNotificationNow(
                org.mockito.ArgumentMatchers.anyCollection(),
                eq("title"),
                eq("body"));
    }


    @Test
    void inactiveCleanup_readsNextBatchByTokenIdCursor() {
        java.util.List<FcmToken> firstBatch = new java.util.ArrayList<>();
        for (long id = 1; id <= 20; id++) {
            FcmToken token = mock(FcmToken.class);
            when(token.getTokenId()).thenReturn(id);
            when(token.getToken()).thenReturn("token-" + id);
            when(token.getEmployeeId()).thenReturn(id);
            when(tokenRepository.findByToken("token-" + id))
                    .thenReturn(Optional.empty());
            firstBatch.add(token);
        }

        when(tokenRepository.findInactiveTokensBatch(any(LocalDateTime.class), isNull(), eq(20)))
                .thenReturn(firstBatch);
        when(tokenRepository.findInactiveTokensBatch(any(LocalDateTime.class), eq(20L), eq(20)))
                .thenReturn(java.util.List.of());

        notificationService.cleanupInactiveTokens(
                LocalDateTime.of(2026, 10, 1, 0, 0),
                3);

        verify(tokenRepository).findInactiveTokensBatch(
                any(LocalDateTime.class), isNull(), eq(20));
        verify(tokenRepository).findInactiveTokensBatch(
                any(LocalDateTime.class), eq(20L), eq(20));
    }

    @Test
    void inactiveCleanup_skipsDeleteWhenTokenOwnerChangedBeforeSerializedCleanup() {
        FcmToken staleSnapshot = mock(FcmToken.class);
        FcmToken currentToken = mock(FcmToken.class);
        when(staleSnapshot.getTokenId()).thenReturn(1L);
        when(staleSnapshot.getToken()).thenReturn(TOKEN);
        when(staleSnapshot.getEmployeeId()).thenReturn(OLD_EMPLOYEE_ID);
        when(currentToken.getEmployeeId()).thenReturn(NEW_EMPLOYEE_ID);

        when(tokenRepository.findInactiveTokensBatch(any(LocalDateTime.class), isNull(), eq(20)))
                .thenReturn(java.util.List.of(staleSnapshot));
        when(tokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(currentToken));

        notificationService.cleanupInactiveTokens(LocalDateTime.of(2026, 10, 1, 0, 0), 3);

        verify(fcmService, never()).unsubscribeTopics(TOKEN, OLD_EMPLOYEE_ID);
        verify(tokenRepository, never()).deleteByTokenAndEmployeeId(TOKEN, OLD_EMPLOYEE_ID);
    }

    @Test
    void inactiveCleanup_deleteRace_restoresCurrentSessionTopicBinding() {
        FcmToken staleSnapshot = mock(FcmToken.class);
        FcmToken cleanupCandidate = mock(FcmToken.class);
        FcmToken rebound = mock(FcmToken.class);
        UpdatedAudit updatedAudit = mock(UpdatedAudit.class);

        when(staleSnapshot.getTokenId()).thenReturn(1L);
        when(staleSnapshot.getToken()).thenReturn(TOKEN);
        when(staleSnapshot.getEmployeeId()).thenReturn(NEW_EMPLOYEE_ID);
        when(staleSnapshot.getAuthSessionMarker()).thenReturn("session-old");

        when(cleanupCandidate.getEmployeeId()).thenReturn(NEW_EMPLOYEE_ID);
        when(cleanupCandidate.getAuthSessionMarker()).thenReturn("session-old");
        when(cleanupCandidate.getUpdatedAudit()).thenReturn(updatedAudit);
        when(updatedAudit.getUpdatedAt())
                .thenReturn(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(rebound.getEmployeeId()).thenReturn(NEW_EMPLOYEE_ID);
        when(rebound.getAuthSessionMarker()).thenReturn("session-new");

        when(tokenRepository.findInactiveTokensBatch(any(LocalDateTime.class), isNull(), eq(20)))
                .thenReturn(java.util.List.of(staleSnapshot));
        when(tokenRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(cleanupCandidate), Optional.of(rebound));
        when(fcmService.unsubscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(tokenRepository.deleteByTokenAndBinding(
                TOKEN,
                NEW_EMPLOYEE_ID,
                "session-old")).thenReturn(0L);
        when(fcmService.subscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(true));

        notificationService.cleanupInactiveTokens(
                LocalDateTime.of(2026, 10, 1, 0, 0),
                3);

        verify(tokenRepository).deleteByTokenAndBinding(
                TOKEN,
                NEW_EMPLOYEE_ID,
                "session-old");
        verify(fcmService).subscribeTopics(TOKEN, NEW_EMPLOYEE_ID);
    }

}
