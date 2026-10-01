package com.dyinfotech.annualleavebackend.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LeaveKeysetIndexRegressionTest {

    @Test
    void freshSchema_containsLeaveKeysetIndexes() throws IOException {
        assertKeysetIndexes(normalize(Files.readString(Path.of("sql/schema.sql"))));
    }

    @Test
    void mainMigration_containsLeaveKeysetIndexes() throws IOException {
        assertKeysetIndexes(normalize(
                Files.readString(Path.of("sql/migration_v2_0_oracle.sql"))));
    }

    private void assertKeysetIndexes(String sql) {
        assertTrue(sql.contains("ix_leave_request_status_created"));
        assertTrue(sql.contains(
                "on leave_request(status, created_at, leave_request_id)"));
        assertTrue(sql.contains("ix_leave_request_employee_created"));
        assertTrue(sql.contains(
                "on leave_request(employee_id, created_at, leave_request_id)"));
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
