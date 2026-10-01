-- =====================================================================
-- annual-leave v2 post-migration Oracle repair
-- Fixes legacy NUMBER/FLOAT columns that Hibernate 6 OracleDialect expects
-- as BINARY_FLOAT for Java Float mappings.
--
-- Safe to rerun. Handles a partially committed temp-column conversion.
-- Run after v1 -> v2 migration if application startup reports:
--   Schema validation: found [number], but expecting [binary_float]
-- =====================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK;
SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE normalize_binary_float(
        p_table_name   VARCHAR2,
        p_column_name  VARCHAR2,
        p_not_null     BOOLEAN,
        p_default_zero BOOLEAN
    ) IS
        v_data_type       VARCHAR2(128);
        v_original_count  NUMBER;
        v_temp_count      NUMBER;
        v_null_count      NUMBER;
        v_conversion_gap  NUMBER;
        v_temp_column     VARCHAR2(128);
    BEGIN
        v_temp_column := p_column_name || '__BF';

        SELECT COUNT(*)
          INTO v_original_count
          FROM user_tab_columns
         WHERE table_name = UPPER(p_table_name)
           AND column_name = UPPER(p_column_name);

        SELECT COUNT(*)
          INTO v_temp_count
          FROM user_tab_columns
         WHERE table_name = UPPER(p_table_name)
           AND column_name = UPPER(v_temp_column);

        IF v_original_count = 0 AND v_temp_count = 1 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE ' || p_table_name
                || ' RENAME COLUMN ' || v_temp_column
                || ' TO ' || p_column_name;
            v_original_count := 1;
            v_temp_count := 0;
        END IF;

        IF v_original_count <> 1 THEN
            RAISE_APPLICATION_ERROR(
                -20050,
                'FLOAT 타입 정규화 대상 컬럼이 없습니다. '
                || UPPER(p_table_name) || '.' || UPPER(p_column_name)
            );
        END IF;

        SELECT data_type
          INTO v_data_type
          FROM user_tab_columns
         WHERE table_name = UPPER(p_table_name)
           AND column_name = UPPER(p_column_name);

        IF v_data_type = 'BINARY_FLOAT' THEN
            IF v_temp_count = 1 THEN
                EXECUTE IMMEDIATE
                    'ALTER TABLE ' || p_table_name
                    || ' DROP COLUMN ' || v_temp_column;
            END IF;
        ELSE
            IF v_data_type NOT IN ('NUMBER', 'FLOAT', 'BINARY_DOUBLE') THEN
                RAISE_APPLICATION_ERROR(
                    -20051,
                    '지원하지 않는 기존 숫자 타입입니다. '
                    || UPPER(p_table_name) || '.' || UPPER(p_column_name)
                    || '=' || v_data_type
                );
            END IF;

            IF p_not_null THEN
                EXECUTE IMMEDIATE
                    'SELECT COUNT(*) FROM ' || p_table_name
                    || ' WHERE ' || p_column_name || ' IS NULL'
                    INTO v_null_count;

                IF v_null_count > 0 THEN
                    RAISE_APPLICATION_ERROR(
                        -20052,
                        'NOT NULL 대상 컬럼에 NULL 데이터가 존재합니다. '
                        || UPPER(p_table_name) || '.' || UPPER(p_column_name)
                        || ', count=' || v_null_count
                    );
                END IF;
            END IF;

            IF v_temp_count = 1 THEN
                EXECUTE IMMEDIATE
                    'ALTER TABLE ' || p_table_name
                    || ' DROP COLUMN ' || v_temp_column;
            END IF;

            EXECUTE IMMEDIATE
                'ALTER TABLE ' || p_table_name
                || ' ADD (' || v_temp_column || ' BINARY_FLOAT)';

            EXECUTE IMMEDIATE
                'UPDATE ' || p_table_name
                || ' SET ' || v_temp_column
                || ' = TO_BINARY_FLOAT(' || p_column_name || ')';

            EXECUTE IMMEDIATE
                'SELECT COUNT(*) FROM ' || p_table_name
                || ' WHERE ' || p_column_name || ' IS NOT NULL'
                || ' AND ' || v_temp_column || ' IS NULL'
                INTO v_conversion_gap;

            IF v_conversion_gap > 0 THEN
                RAISE_APPLICATION_ERROR(
                    -20053,
                    'BINARY_FLOAT 변환 중 값 누락이 발생했습니다. '
                    || UPPER(p_table_name) || '.' || UPPER(p_column_name)
                    || ', count=' || v_conversion_gap
                );
            END IF;

            EXECUTE IMMEDIATE
                'ALTER TABLE ' || p_table_name
                || ' DROP COLUMN ' || p_column_name;

            EXECUTE IMMEDIATE
                'ALTER TABLE ' || p_table_name
                || ' RENAME COLUMN ' || v_temp_column
                || ' TO ' || p_column_name;
        END IF;

        IF p_default_zero AND p_not_null THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE ' || p_table_name
                || ' MODIFY (' || p_column_name || ' DEFAULT 0 NOT NULL)';
        ELSIF p_default_zero THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE ' || p_table_name
                || ' MODIFY (' || p_column_name || ' DEFAULT 0)';
        ELSIF p_not_null THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE ' || p_table_name
                || ' MODIFY (' || p_column_name || ' NOT NULL)';
        END IF;

        DBMS_OUTPUT.PUT_LINE(
            UPPER(p_table_name) || '.' || UPPER(p_column_name)
            || ' -> BINARY_FLOAT');
    END;
BEGIN
    normalize_binary_float('EMPLOYEE', 'CURR_TOTAL_LEAVE_DAYS', TRUE, TRUE);
    normalize_binary_float('EMPLOYEE', 'PREV_TOTAL_LEAVE_DAYS', FALSE, FALSE);

    normalize_binary_float('LEAVE_REQUEST', 'USE_DAYS', TRUE, FALSE);
    normalize_binary_float('LEAVE_REQUEST', 'PREV_TOTAL_LEAVE_DAYS', TRUE, FALSE);
    normalize_binary_float('LEAVE_REQUEST', 'CURR_TOTAL_LEAVE_DAYS', TRUE, FALSE);

    normalize_binary_float('LEAVE_ADJUSTMENT', 'LEAVE_DAYS', TRUE, FALSE);
END;
/

COMMENT ON COLUMN employee.curr_total_leave_days IS '현재 기준연도의 총 연차일수';
COMMENT ON COLUMN employee.prev_total_leave_days IS '이전 연차 기준연도의 총 연차일수';
COMMENT ON COLUMN leave_request.use_days IS '사용 연차 일수';
COMMENT ON COLUMN leave_request.prev_total_leave_days IS '신청 시점 이전 기준연도의 총 연차일수 snapshot';
COMMENT ON COLUMN leave_request.curr_total_leave_days IS '신청 시점 현재 기준연도의 총 연차일수 snapshot';
COMMENT ON COLUMN leave_adjustment.leave_days IS '조정 연차 일수';

PROMPT Verify repaired column types

SELECT table_name,
       column_name,
       data_type,
       nullable,
       data_default
  FROM user_tab_columns
 WHERE (table_name = 'EMPLOYEE'
        AND column_name IN ('CURR_TOTAL_LEAVE_DAYS', 'PREV_TOTAL_LEAVE_DAYS'))
    OR (table_name = 'LEAVE_REQUEST'
        AND column_name IN ('USE_DAYS', 'PREV_TOTAL_LEAVE_DAYS', 'CURR_TOTAL_LEAVE_DAYS'))
    OR (table_name = 'LEAVE_ADJUSTMENT'
        AND column_name = 'LEAVE_DAYS')
 ORDER BY table_name, column_id;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_columns
     WHERE (
            (table_name = 'EMPLOYEE'
             AND column_name IN ('CURR_TOTAL_LEAVE_DAYS', 'PREV_TOTAL_LEAVE_DAYS'))
         OR (table_name = 'LEAVE_REQUEST'
             AND column_name IN ('USE_DAYS', 'PREV_TOTAL_LEAVE_DAYS', 'CURR_TOTAL_LEAVE_DAYS'))
         OR (table_name = 'LEAVE_ADJUSTMENT'
             AND column_name = 'LEAVE_DAYS')
     )
       AND data_type <> 'BINARY_FLOAT';

    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(
            -20054,
            'BINARY_FLOAT 변환이 완료되지 않은 컬럼이 있습니다. count=' || v_count
        );
    END IF;
END;
/

PROMPT Post-migration BINARY_FLOAT repair completed
