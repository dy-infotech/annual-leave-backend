package com.dyinfotech.annualleavebackend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HealthControllerRegressionTest {

    @Test
    void health_returnsStableReadinessPayload() {
        var response = new HealthController().health();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }
}
