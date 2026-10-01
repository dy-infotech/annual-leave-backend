package com.dyinfotech.annualleavebackend.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class SchemaCommentRegressionTest {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+([A-Za-z0-9_]+)\\s*\\((.*?)\\)\\s*(?:TABLESPACE\\s+[A-Za-z0-9_]+\\s*)?;");

    private static final Pattern COLUMN_DEFINITION = Pattern.compile(
            "(?im)^\\s*([A-Za-z][A-Za-z0-9_]*)\\s+"
                    + "(?:NUMBER(?:\\([^)]*\\))?|VARCHAR2(?:\\([^)]*\\))?|"
                    + "TIMESTAMP(?:\\([^)]*\\))?|DATE|BINARY_FLOAT|CLOB)\\b");

    private static final Pattern TABLE_COMMENT = Pattern.compile(
            "(?is)COMMENT\\s+ON\\s+TABLE\\s+([A-Za-z0-9_]+)"
                    + "\\s+IS\\s+'((?:''|[^'])*)'\\s*;");

    private static final Pattern COLUMN_COMMENT = Pattern.compile(
            "(?is)COMMENT\\s+ON\\s+COLUMN\\s+([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)"
                    + "\\s+IS\\s+'((?:''|[^'])*)'\\s*;");

    @Test
    void freshSchema_hasCommentForEveryTableAndColumn() throws IOException {
        String schema = Files.readString(Path.of("sql/schema.sql"));

        Set<String> tables = new LinkedHashSet<>();
        Set<String> columns = new LinkedHashSet<>();

        Matcher tableMatcher = CREATE_TABLE.matcher(schema);
        while (tableMatcher.find()) {
            String table = tableMatcher.group(1).toLowerCase();
            assertTrue(tables.add(table), "중복 CREATE TABLE: " + table);

            Matcher columnMatcher = COLUMN_DEFINITION.matcher(tableMatcher.group(2));
            while (columnMatcher.find()) {
                columns.add(table + "." + columnMatcher.group(1).toLowerCase());
            }
        }

        assertFalse(tables.isEmpty(), "schema.sql에서 CREATE TABLE을 찾지 못했습니다.");
        assertFalse(columns.isEmpty(), "schema.sql에서 컬럼 정의를 찾지 못했습니다.");

        Set<String> commentedTables = new LinkedHashSet<>();
        Matcher tableCommentMatcher = TABLE_COMMENT.matcher(schema);
        while (tableCommentMatcher.find()) {
            String table = tableCommentMatcher.group(1).toLowerCase();
            String description = tableCommentMatcher.group(2).trim();
            assertFalse(description.isEmpty(), "빈 TABLE COMMENT: " + table);
            assertTrue(commentedTables.add(table), "중복 TABLE COMMENT: " + table);
        }

        Set<String> commentedColumns = new LinkedHashSet<>();
        Matcher columnCommentMatcher = COLUMN_COMMENT.matcher(schema);
        while (columnCommentMatcher.find()) {
            String column = columnCommentMatcher.group(1).toLowerCase()
                    + "." + columnCommentMatcher.group(2).toLowerCase();
            String description = columnCommentMatcher.group(3).trim();
            assertFalse(description.isEmpty(), "빈 COLUMN COMMENT: " + column);
            assertTrue(commentedColumns.add(column), "중복 COLUMN COMMENT: " + column);
        }

        assertEquals(tables, commentedTables,
                "모든 테이블에 COMMENT ON TABLE이 있어야 합니다.");
        assertEquals(columns, commentedColumns,
                "모든 컬럼에 COMMENT ON COLUMN이 있어야 합니다.");
    }
}
