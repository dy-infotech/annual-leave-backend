package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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

import com.dyinfotech.annualleavebackend.common.IpContext;
import com.dyinfotech.annualleavebackend.domain.FcmToken;
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

        notificationService.syncToken(NEW_EMPLOYEE_ID, TOKEN, DEVICE_OS).join();

        verify(fcmService, times(1)).unsubscribeTopics(TOKEN, OLD_EMPLOYEE_ID);
        verify(fcmService, times(2)).subscribeTopics(TOKEN, NEW_EMPLOYEE_ID);
        verify(tokenRepository, times(1)).updateTokenAndTouch(
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

        notificationService.syncToken(NEW_EMPLOYEE_ID, TOKEN, DEVICE_OS).join();

        verify(fcmService, times(2)).unsubscribeTopics(TOKEN, OLD_EMPLOYEE_ID);
        verify(fcmService, times(1)).subscribeTopics(TOKEN, NEW_EMPLOYEE_ID);
        verify(tokenRepository, times(1)).updateTokenAndTouch(
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
        verify(tokenRepository, never()).save(any(FcmToken.class));
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
        }).when(tokenRepository).updateTokenAndTouch(
                eq(NEW_EMPLOYEE_ID),
                eq(DEVICE_OS),
                any(LocalDateTime.class),
                eq(TOKEN)
        );

        notificationService.syncToken(NEW_EMPLOYEE_ID, TOKEN, DEVICE_OS).join();

        assertEquals("127.0.0.1", IpContext.get());
    }
    @Test
    void logout_deletesTokenOnlyAfterTopicUnsubscribeSucceeds() {
        CompletableFuture<Boolean> unsubscribeFuture = new CompletableFuture<>();
        when(tokenRepository.findByToken(TOKEN)).thenReturn(Optional.empty());
        when(fcmService.unsubscribeTopics(TOKEN, NEW_EMPLOYEE_ID)).thenReturn(unsubscribeFuture);

        CompletableFuture<Void> result = notificationService.logoutToken(TOKEN, NEW_EMPLOYEE_ID);

        verify(tokenRepository, never()).deleteByToken(TOKEN);

        unsubscribeFuture.complete(true);
        result.join();

        verify(tokenRepository, times(1)).deleteByToken(TOKEN);
    }

    @Test
    void logout_unsubscribeFailure_doesNotDeleteToken() {
        when(tokenRepository.findByToken(TOKEN)).thenReturn(Optional.empty());
        when(fcmService.unsubscribeTopics(TOKEN, NEW_EMPLOYEE_ID))
                .thenReturn(CompletableFuture.completedFuture(false));

        assertThrows(
                CompletionException.class,
                () -> notificationService.logoutToken(TOKEN, NEW_EMPLOYEE_ID).join()
        );

        verify(tokenRepository, never()).deleteByToken(TOKEN);
    }
}
