package com.dyinfotech.annualleavebackend.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerRegressionTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            new ErrorResponseFactory(
                    Clock.fixed(
                            Instant.parse("2026-09-30T00:00:00Z"),
                            ZoneId.of("Asia/Seoul"))));

    @Test
    void oracleUniqueViolation_returnsConflict() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "duplicate",
                        new SQLException(
                                "ORA-00001: unique constraint violated",
                                "23000",
                                1));

        var response = handler.handleDataIntegrity(
                exception,
                new MockHttpServletRequest("POST", "/api/admin/test"));

        assertEquals(409, response.getStatusCode().value());
        assertEquals(409, response.getBody().getStatus());
    }

    @Test
    void nonUniqueIntegrityViolation_remainsServerError() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "foreign key",
                        new SQLException(
                                "ORA-02291: integrity constraint violated - parent key not found",
                                "23000",
                                2291));

        var response = handler.handleDataIntegrity(
                exception,
                new MockHttpServletRequest("POST", "/api/admin/test"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals(500, response.getBody().getStatus());
    }
}
