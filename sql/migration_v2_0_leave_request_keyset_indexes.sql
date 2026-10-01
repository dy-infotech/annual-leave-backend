-- =====================================================================
-- annual-leave develop_v2.0
-- LEAVE_REQUEST keyset pagination indexes
--
-- Use on an already-created v2 database before deploying keyset pagination.
-- Fresh installs and legacy -> v2 migrations already create these indexes.
-- Oracle DDL performs implicit commit; back up the DB before execution.
-- =====================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK;

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE ensure_index(p_name VARCHAR2, p_ddl VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM user_indexes
         WHERE index_name = UPPER(p_name);

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
        END IF;
    END;
BEGIN
    ensure_index(
        'IX_LEAVE_REQUEST_STATUS_CREATED',
        'CREATE INDEX ix_leave_request_status_created '
        || 'ON leave_request(status, created_at, leave_request_id)');

    ensure_index(
        'IX_LEAVE_REQUEST_EMPLOYEE_CREATED',
        'CREATE INDEX ix_leave_request_employee_created '
        || 'ON leave_request(employee_id, created_at, leave_request_id)');
END;
/

SELECT index_name, table_name
  FROM user_indexes
 WHERE index_name IN (
       'IX_LEAVE_REQUEST_STATUS_CREATED',
       'IX_LEAVE_REQUEST_EMPLOYEE_CREATED'
 )
 ORDER BY index_name;
