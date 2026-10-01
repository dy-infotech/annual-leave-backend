package com.dyinfotech.annualleavebackend.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MigrationIdempotencyRegressionTest {

    @Test
    void migration_recreatesStaleEmployeeOrganizationBackupFromV1Source()
            throws IOException {
        String migration = normalize(
                Files.readString(Path.of("sql/migration_v2_0_oracle.sql")));

        assertTrue(migration.contains(
                "drop table employee_org_legacy purge"));
        assertTrue(migration.contains(
                "create table employee_org_legacy as select employee_id, "
                        + "department as department_name, team as team_name from employee"));
        assertTrue(migration.indexOf("drop table employee_org_legacy purge")
                < migration.indexOf("create table employee_org_legacy as"));
    }

    @Test
    void migration_rebuildsLeaveIdempotencyConstraintInsteadOfEnablingStaleOne()
            throws IOException {
        String migration = normalize(
                Files.readString(Path.of("sql/migration_v2_0_oracle.sql")));

        int dropConstraint = migration.indexOf(
                "alter table leave_request drop constraint "
                        + "uk_leave_request_create_request");
        int reset = migration.indexOf(
                "update leave_request set create_request_key = null, "
                        + "create_request_hash = null");
        int addConstraint = migration.indexOf(
                "alter table leave_request add constraint "
                        + "uk_leave_request_create_request unique "
                        + "(employee_id, create_request_key)");

        assertTrue(dropConstraint >= 0);
        assertTrue(reset > dropConstraint);
        assertTrue(addConstraint > reset);
        assertFalse(migration.contains(
                "enable validate constraint uk_leave_request_create_request"));
        assertFalse(migration.contains(
                "enable validate constraint ck_leave_request_create_pair"));
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
