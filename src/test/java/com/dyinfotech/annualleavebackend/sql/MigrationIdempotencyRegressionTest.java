package com.dyinfotech.annualleavebackend.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MigrationIdempotencyRegressionTest {

    @Test
    void leaveIdempotencyRunsBeforeOrganizationDdl() throws IOException {
        String migration = normalize(
                Files.readString(Path.of("sql/migration_v2_0_oracle.sql")));

        int leave = migration.indexOf(
                "prompt [1.5/9] add leave request idempotency metadata before organization ddl");
        int preserveOrg = migration.indexOf(
                "prompt [2/9] preserve legacy organization data");
        int createDepartment = migration.indexOf(
                "prompt [3/9] create department");

        assertTrue(leave >= 0);
        assertTrue(preserveOrg > leave);
        assertTrue(createDepartment > leave);
    }

    @Test
    void migrationUsesConditionalUniqueIndexForLeaveIdempotency()
            throws IOException {
        String migration = normalize(
                Files.readString(Path.of("sql/migration_v2_0_oracle.sql")));

        assertTrue(migration.contains(
                "create unique index uk_leave_request_create_request "
                        + "on leave_request ( case when create_request_key is not null "
                        + "then employee_id end, case when create_request_key is not null "
                        + "then create_request_key end )"));

        assertFalse(migration.contains(
                "unique (employee_id, create_request_key)"));
        assertFalse(migration.contains(
                "enable validate constraint uk_leave_request_create_request"));
    }

    @Test
    void freshSchemaUsesSameConditionalUniqueIndex() throws IOException {
        String schema = normalize(
                Files.readString(Path.of("sql/schema.sql")));

        assertTrue(schema.contains(
                "create unique index uk_leave_request_create_request "
                        + "on leave_request ( case when create_request_key is not null "
                        + "then employee_id end, case when create_request_key is not null "
                        + "then create_request_key end )"));
        assertFalse(schema.contains(
                "constraint uk_leave_request_create_request "
                        + "unique (employee_id, create_request_key)"));
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
