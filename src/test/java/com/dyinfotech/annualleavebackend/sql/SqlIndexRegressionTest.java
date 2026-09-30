package com.dyinfotech.annualleavebackend.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SqlIndexRegressionTest {

    @Test
    void freshSchema_containsOrganizationLookupIndexes() throws IOException {
        String schema = normalize(Files.readString(Path.of("sql/schema.sql")));

        assertTrue(schema.contains(
                "create index ix_team_department on team(department_id);"));
        assertTrue(schema.contains(
                "create index ix_team_manager_project_manager on team_manager(project_manager_id);"));
    }

    @Test
    void migration_containsOrganizationLookupIndexes() throws IOException {
        String migration = normalize(Files.readString(Path.of("sql/migration_v2_0_oracle.sql")));

        assertTrue(migration.contains(
                "create index ix_team_department on team(department_id)"));
        assertTrue(migration.contains(
                "create index ix_team_manager_project_manager on team_manager(project_manager_id)"));
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
