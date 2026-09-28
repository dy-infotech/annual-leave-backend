-- =====================================================================
-- annual-leave develop_v2.0
-- TEAM create request idempotency metadata
--
-- Use only when an earlier draft of migration_v2_0_oracle.sql has already
-- been applied and TEAM exists without CREATE_REQUEST_KEY/HASH.
-- Fresh v1 -> v2 migrations already create these columns and constraints.
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
     WHERE table_name = 'TEAM'
       AND column_name = 'CREATE_REQUEST_KEY';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE team ADD (create_request_key VARCHAR2(128 CHAR))';
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_columns
     WHERE table_name = 'TEAM'
       AND column_name = 'CREATE_REQUEST_HASH';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE team ADD (create_request_hash VARCHAR2(64 CHAR))';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_constraints
     WHERE table_name = 'TEAM'
       AND constraint_name = 'UK_TEAM_CREATE_REQUEST';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE team ADD CONSTRAINT uk_team_create_request UNIQUE (create_request_key)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_constraints
     WHERE table_name = 'TEAM'
       AND constraint_name = 'CK_TEAM_CREATE_REQUEST_PAIR';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE team ADD CONSTRAINT ck_team_create_request_pair CHECK '
            || '((create_request_key IS NULL AND create_request_hash IS NULL) '
            || 'OR (create_request_key IS NOT NULL AND create_request_hash IS NOT NULL))';
    END IF;
END;
/

SELECT column_name, data_type, data_length
  FROM user_tab_columns
 WHERE table_name = 'TEAM'
   AND column_name IN ('CREATE_REQUEST_KEY', 'CREATE_REQUEST_HASH')
 ORDER BY column_name;
