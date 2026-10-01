package com.dyinfotech.annualleavebackend.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MigrationPreflightRegressionTest {

    @Test
    void migration_checksV1ShapeBeforeLegacyQueries() throws IOException {
        String sql = normalize(Files.readString(
                Path.of("sql/migration_v2_0_oracle.sql")));

        int preflight = sql.indexOf(
                "prompt [0/9] validate v1 schema shape before migration");
        int legacyPrecheck = sql.indexOf(
                "prompt [1/9] precheck legacy organization data");

        assertTrue(preflight >= 0);
        assertTrue(legacyPrecheck > preflight);
        assertTrue(sql.contains("require_column('employee', 'department')"));
        assertTrue(sql.contains("require_column('team', 'project_manager_id')"));
        assertTrue(sql.contains("reject_column('employee', 'department_id')"));
        assertTrue(sql.contains("reject_table('department')"));
        assertTrue(sql.contains("reject_table('team_legacy')"));
        assertTrue(sql.contains(
                "reject_column('leave_request', 'create_request_key')"));
    }

    @Test
    void passwordResetTokenCreationIsRetrySafe() throws IOException {
        String sql = normalize(Files.readString(
                Path.of("sql/migration_v2_0_oracle.sql")));

        assertTrue(sql.contains(
                "where table_name = 'password_reset_token'"));
        assertTrue(sql.contains(
                "where index_name = 'ix_password_reset_employee'"));
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
