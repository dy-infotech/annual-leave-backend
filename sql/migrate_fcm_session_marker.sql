-- =====================================================================
-- Bind FCM token ownership to the Web SSO refresh session that last synced it.
-- Existing/native rows remain compatible because the marker is nullable.
-- =====================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_columns
     WHERE table_name = 'FCM_TOKEN'
       AND column_name = 'AUTH_SESSION_MARKER';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE fcm_token ADD (auth_session_marker VARCHAR2(64 CHAR))';
    END IF;
END;
/
