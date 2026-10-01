-- =====================================================================
-- annual-leave develop_v2.0
-- LEAVE_REQUEST create request idempotency metadata
--
-- Use when a v2 database already exists without these columns/constraints.
-- Fresh v1 -> v2 migrations include the same changes in migration_v2_0_oracle.sql.
-- Oracle DDL performs implicit commit; back up the DB before execution.
-- =====================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK;

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_columns
     WHERE table_name = 'LEAVE_REQUEST'
       AND column_name = 'CREATE_REQUEST_KEY';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE leave_request ADD (create_request_key VARCHAR2(128 CHAR))';
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_columns
     WHERE table_name = 'LEAVE_REQUEST'
       AND column_name = 'CREATE_REQUEST_HASH';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE leave_request ADD (create_request_hash VARCHAR2(64 CHAR))';
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM user_constraints
     WHERE table_name = 'LEAVE_REQUEST'
       AND constraint_name = 'UK_LEAVE_REQUEST_CREATE_REQUEST';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE leave_request ADD CONSTRAINT uk_leave_request_create_request '
            || 'UNIQUE (employee_id, create_request_key)';
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM user_constraints
     WHERE table_name = 'LEAVE_REQUEST'
       AND constraint_name = 'CK_LEAVE_REQUEST_CREATE_PAIR';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE leave_request ADD CONSTRAINT ck_leave_request_create_pair CHECK '
            || '((create_request_key IS NULL AND create_request_hash IS NULL) '
            || 'OR (create_request_key IS NOT NULL AND create_request_hash IS NOT NULL))';
    END IF;
END;
/

SELECT column_name, data_type, data_length
  FROM user_tab_columns
 WHERE table_name = 'LEAVE_REQUEST'
   AND column_name IN ('CREATE_REQUEST_KEY', 'CREATE_REQUEST_HASH')
 ORDER BY column_name;
