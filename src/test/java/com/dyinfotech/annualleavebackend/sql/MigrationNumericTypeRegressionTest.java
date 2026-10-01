package com.dyinfotech.annualleavebackend.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MigrationNumericTypeRegressionTest {

    @Test
    void migrationNormalizesLegacyFloatColumnsBeforeOrganizationDdl()
            throws IOException {
        String sql = normalize(Files.readString(
                Path.of("sql/migration_v2_0_oracle.sql")));

        int normalize = sql.indexOf(
                "prompt [1.6/9] normalize legacy oracle floating-point columns before organization ddl");
        int preserveOrg = sql.indexOf(
                "prompt [2/9] preserve legacy organization data");

        assertTrue(normalize >= 0);
        assertTrue(preserveOrg > normalize);

        assertTrue(sql.contains(
                "normalize_binary_float('employee', 'curr_total_leave_days', true, true)"));
        assertTrue(sql.contains(
                "normalize_binary_float('employee', 'prev_total_leave_days', false, false)"));
        assertTrue(sql.contains(
                "normalize_binary_float('leave_request', 'use_days', true, false)"));
        assertTrue(sql.contains(
                "normalize_binary_float('leave_request', 'prev_total_leave_days', true, false)"));
        assertTrue(sql.contains(
                "normalize_binary_float('leave_request', 'curr_total_leave_days', true, false)"));
        assertTrue(sql.contains(
                "normalize_binary_float('leave_adjustment', 'leave_days', true, false)"));

        assertTrue(sql.contains("to_binary_float("));
    }

    @Test
    void postMigrationRepairCoversSameSixColumns() throws IOException {
        String sql = normalize(Files.readString(
                Path.of("sql/repair_v2_0_binary_float_oracle.sql")));

        assertTrue(sql.contains(
                "normalize_binary_float('employee', 'curr_total_leave_days', true, true)"));
        assertTrue(sql.contains(
                "normalize_binary_float('employee', 'prev_total_leave_days', false, false)"));
        assertTrue(sql.contains(
                "normalize_binary_float('leave_request', 'use_days', true, false)"));
        assertTrue(sql.contains(
                "normalize_binary_float('leave_request', 'prev_total_leave_days', true, false)"));
        assertTrue(sql.contains(
                "normalize_binary_float('leave_request', 'curr_total_leave_days', true, false)"));
        assertTrue(sql.contains(
                "normalize_binary_float('leave_adjustment', 'leave_days', true, false)"));
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
