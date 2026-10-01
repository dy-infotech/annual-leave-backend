package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.IpContext;

class AuthRateLimitServiceRegressionTest {

    private final AuthRateLimitService service = new AuthRateLimitService();

    @AfterEach
    void tearDown() {
        IpContext.clear();
    }

    @Test
    void signInIdentityLimit_isSharedAcrossDifferentSourceIps() {
        for (int i = 0; i < 10; i++) {
            IpContext.set("10.0.0." + i);
            assertDoesNotThrow(() -> service.checkSignIn("EMP-001"));
        }

        IpContext.set("10.0.1.1");
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.checkSignIn("EMP-001"));

        assertEquals(429, exception.getStatusCode().value());
    }

    @Test
    void successfulSignIn_clearsIdentityLimiterRegardlessOfSourceIp() {
        IpContext.set("10.0.0.1");
        for (int i = 0; i < 10; i++) {
            service.checkSignIn("EMP-002");
        }

        service.clearSignIn("EMP-002");

        IpContext.set("10.0.0.99");
        assertDoesNotThrow(() -> service.checkSignIn("EMP-002"));
    }
}
