package com.dyinfotech.annualleavebackend.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // LEAVE_REQUEST 부분 실행 흔적은 [1.5/9]가 스스로 정리하므로
        // preflight 단계에서 차단하면 안 된다.
        assertFalse(sql.contains(
                "reject_column('leave_request', 'create_request_key')"));
        assertFalse(sql.contains(
                "reject_column('leave_request', 'create_request_hash')"));
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
