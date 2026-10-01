package com.dyinfotech.annualleavebackend.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MigrationIdempotencyRegressionTest {

    @Test
    void migration_resetsPartialIdempotencyMetadataBeforeUniqueConstraint()
            throws IOException {
        String migration = normalize(
                Files.readString(Path.of("sql/migration_v2_0_oracle.sql")));

        int reset = migration.indexOf(
                "update leave_request set create_request_key = null, "
                        + "create_request_hash = null");
        int unique = migration.indexOf(
                "alter table leave_request add constraint "
                        + "uk_leave_request_create_request unique "
                        + "(employee_id, create_request_key)");

        assertTrue(reset >= 0,
                "부분 마이그레이션의 멱등성 메타데이터 초기화가 필요합니다.");
        assertTrue(unique > reset,
                "UNIQUE 제약은 기존 멱등성 메타데이터를 정리한 뒤 생성해야 합니다.");
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
