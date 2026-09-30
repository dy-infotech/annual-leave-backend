-- =====================================================================
-- 연차/자산관리 공통 SSO Refresh Token Rotation migration
--
-- 지원 상태:
--   1) 기존 자산관리 RESOURCE_REFRESH_SESSION -> AUTH_REFRESH_SESSION rename
--   2) 이미 AUTH_REFRESH_SESSION이 있는 구 hardening DB -> tablespace/index 정규화
--   3) 두 테이블 모두 없는 기존 연차 DB -> AUTH_REFRESH_SESSION 신규 생성
--
-- 최종 상태:
--   TABLE  AUTH_REFRESH_SESSION -> COMMON_DATA
--   INDEX  PK_AUTH_REFRESH_SESSION / IX_ARS_* -> COMMON_INDEX
--
-- 선행:
--   SYSDBA/DBA가 sql/dba_prepare_common_auth_tablespace.sql에 준하는 방식으로
--   COMMON_DATA / COMMON_INDEX를 준비하고 HR_USER quota를 부여해야 한다.
--
-- Oracle DDL은 implicit COMMIT이므로 운영 적용 전 백업한다.
-- RESOURCE_REFRESH_SESSION과 AUTH_REFRESH_SESSION이 동시에 존재하면
-- 데이터를 임의 병합하지 않고 중단한다.
-- =====================================================================

SET SERVEROUTPUT ON;
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK;

DECLARE
    v_common_data  NUMBER;
    v_common_index NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_common_data
      FROM user_tablespaces
     WHERE tablespace_name = 'COMMON_DATA';

    SELECT COUNT(*) INTO v_common_index
      FROM user_tablespaces
     WHERE tablespace_name = 'COMMON_INDEX';

    IF v_common_data = 0 OR v_common_index = 0 THEN
        RAISE_APPLICATION_ERROR(
            -20980,
            'COMMON_DATA/COMMON_INDEX가 없습니다. DBA 준비 SQL을 먼저 실행하세요.');
    END IF;
END;
/

DECLARE
    v_auth     NUMBER;
    v_resource NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_auth
      FROM user_tables
     WHERE table_name = 'AUTH_REFRESH_SESSION';

    SELECT COUNT(*) INTO v_resource
      FROM user_tables
     WHERE table_name = 'RESOURCE_REFRESH_SESSION';

    IF v_auth = 1 AND v_resource = 1 THEN
        RAISE_APPLICATION_ERROR(
            -20981,
            'AUTH_REFRESH_SESSION과 RESOURCE_REFRESH_SESSION이 동시에 존재합니다. 자동 병합하지 않습니다.');
    ELSIF v_auth = 0 AND v_resource = 1 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE resource_refresh_session RENAME TO auth_refresh_session';
        DBMS_OUTPUT.PUT_LINE('TABLE RENAME: RESOURCE_REFRESH_SESSION -> AUTH_REFRESH_SESSION');
    ELSIF v_auth = 1 THEN
        DBMS_OUTPUT.PUT_LINE('TABLE: AUTH_REFRESH_SESSION already exists');
    ELSE
        EXECUTE IMMEDIATE q'[
            CREATE TABLE auth_refresh_session (
                session_id                VARCHAR2(36)      NOT NULL,
                employee_id               NUMBER(19)        NOT NULL,
                token_hash                VARCHAR2(64)      NOT NULL,
                previous_token_hash       VARCHAR2(64)      NULL,
                previous_valid_until      TIMESTAMP(6)      NULL,
                created_at                TIMESTAMP(6)      NOT NULL,
                last_rotated_at           TIMESTAMP(6)      NOT NULL,
                idle_expires_at           TIMESTAMP(6)      NOT NULL,
                absolute_expires_at       TIMESTAMP(6)      NOT NULL,
                revoked_at                TIMESTAMP(6)      NULL,
                revoked_reason            VARCHAR2(40)      NULL,
                rotation_count            NUMBER(10) DEFAULT 0 NOT NULL,
                CONSTRAINT PK_AUTH_REFRESH_SESSION
                    PRIMARY KEY (session_id) USING INDEX TABLESPACE COMMON_INDEX,
                CONSTRAINT FK_ARS_EMPLOYEE
                    FOREIGN KEY (employee_id) REFERENCES employee (employee_id),
                CONSTRAINT CK_ARS_ROTATION_COUNT CHECK (rotation_count >= 0),
                CONSTRAINT CK_ARS_EXPIRY_ORDER CHECK (idle_expires_at <= absolute_expires_at)
            ) TABLESPACE COMMON_DATA
        ]';

        EXECUTE IMMEDIATE
            'CREATE INDEX IX_ARS_EMPLOYEE ON auth_refresh_session (employee_id) TABLESPACE COMMON_INDEX';
        EXECUTE IMMEDIATE
            'CREATE INDEX IX_ARS_EXPIRY ON auth_refresh_session (absolute_expires_at, idle_expires_at) TABLESPACE COMMON_INDEX';

        DBMS_OUTPUT.PUT_LINE('TABLE: AUTH_REFRESH_SESSION created');
    END IF;
END;
/

DECLARE
    PROCEDURE rename_constraint_if_needed(
        p_old_name IN VARCHAR2,
        p_new_name IN VARCHAR2
    ) IS
        v_old_count NUMBER;
        v_new_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_old_count
          FROM user_constraints
         WHERE table_name = 'AUTH_REFRESH_SESSION'
           AND constraint_name = UPPER(p_old_name);

        SELECT COUNT(*) INTO v_new_count
          FROM user_constraints
         WHERE table_name = 'AUTH_REFRESH_SESSION'
           AND constraint_name = UPPER(p_new_name);

        IF v_old_count = 1 AND v_new_count = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE auth_refresh_session RENAME CONSTRAINT '
                || DBMS_ASSERT.SIMPLE_SQL_NAME(p_old_name)
                || ' TO '
                || DBMS_ASSERT.SIMPLE_SQL_NAME(p_new_name);
        ELSIF v_old_count = 1 AND v_new_count = 1 THEN
            RAISE_APPLICATION_ERROR(
                -20982,
                '구/신 constraint가 동시에 존재합니다: ' || p_old_name || ', ' || p_new_name);
        END IF;
    END;
BEGIN
    rename_constraint_if_needed('PK_RESOURCE_REFRESH_SESSION', 'PK_AUTH_REFRESH_SESSION');
    rename_constraint_if_needed('FK_RRS_EMPLOYEE', 'FK_ARS_EMPLOYEE');
    rename_constraint_if_needed('CK_RRS_ROTATION_COUNT', 'CK_ARS_ROTATION_COUNT');
    rename_constraint_if_needed('CK_RRS_EXPIRY_ORDER', 'CK_ARS_EXPIRY_ORDER');
END;
/

DECLARE
    PROCEDURE rename_index_if_needed(
        p_old_name IN VARCHAR2,
        p_new_name IN VARCHAR2
    ) IS
        v_old_count NUMBER;
        v_new_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_old_count
          FROM user_indexes
         WHERE table_name = 'AUTH_REFRESH_SESSION'
           AND index_name = UPPER(p_old_name);

        SELECT COUNT(*) INTO v_new_count
          FROM user_indexes
         WHERE table_name = 'AUTH_REFRESH_SESSION'
           AND index_name = UPPER(p_new_name);

        IF v_old_count = 1 AND v_new_count = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER INDEX '
                || DBMS_ASSERT.SIMPLE_SQL_NAME(p_old_name)
                || ' RENAME TO '
                || DBMS_ASSERT.SIMPLE_SQL_NAME(p_new_name);
        ELSIF v_old_count = 1 AND v_new_count = 1 THEN
            RAISE_APPLICATION_ERROR(
                -20983,
                '구/신 index가 동시에 존재합니다: ' || p_old_name || ', ' || p_new_name);
        END IF;
    END;
BEGIN
    rename_index_if_needed('PK_RESOURCE_REFRESH_SESSION', 'PK_AUTH_REFRESH_SESSION');
    rename_index_if_needed('IX_RRS_EMPLOYEE', 'IX_ARS_EMPLOYEE');
    rename_index_if_needed('IX_RRS_EXPIRY', 'IX_ARS_EXPIRY');
END;
/

DECLARE
    v_tablespace user_tables.tablespace_name%TYPE;
BEGIN
    SELECT tablespace_name
      INTO v_tablespace
      FROM user_tables
     WHERE table_name = 'AUTH_REFRESH_SESSION';

    IF v_tablespace <> 'COMMON_DATA' THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE auth_refresh_session MOVE TABLESPACE COMMON_DATA UPDATE INDEXES';
        DBMS_OUTPUT.PUT_LINE('TABLESPACE MOVE: AUTH_REFRESH_SESSION -> COMMON_DATA');
    END IF;
END;
/

DECLARE
    PROCEDURE rebuild_index_if_needed(p_index_name IN VARCHAR2) IS
        v_tablespace user_indexes.tablespace_name%TYPE;
    BEGIN
        SELECT tablespace_name
          INTO v_tablespace
          FROM user_indexes
         WHERE table_name = 'AUTH_REFRESH_SESSION'
           AND index_name = UPPER(p_index_name);

        IF v_tablespace <> 'COMMON_INDEX' THEN
            EXECUTE IMMEDIATE
                'ALTER INDEX '
                || DBMS_ASSERT.SIMPLE_SQL_NAME(p_index_name)
                || ' REBUILD TABLESPACE COMMON_INDEX';
        END IF;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(
                -20984,
                '필수 index가 없습니다: ' || p_index_name);
    END;
BEGIN
    rebuild_index_if_needed('PK_AUTH_REFRESH_SESSION');
    rebuild_index_if_needed('IX_ARS_EMPLOYEE');
    rebuild_index_if_needed('IX_ARS_EXPIRY');
END;
/

COMMENT ON TABLE auth_refresh_session
    IS '연차/자산관리 공통 Web SSO Refresh Token Rotation 세션. 원문 token은 저장하지 않는다.';

COMMENT ON COLUMN auth_refresh_session.session_id
    IS '공통 Refresh family/session UUID';

COMMENT ON COLUMN auth_refresh_session.token_hash
    IS '현재 opaque refresh token SHA-256(Base64URL) hash';

COMMENT ON COLUMN auth_refresh_session.previous_token_hash
    IS '직전 token hash. 짧은 동시 refresh grace 판별용';

COMMENT ON COLUMN auth_refresh_session.previous_valid_until
    IS '직전 token을 동시 rotation으로 간주할 수 있는 grace 종료시각';

COMMENT ON COLUMN auth_refresh_session.absolute_expires_at
    IS '최초 로그인 기준 절대 만료. rotation으로 연장하지 않는다.';

BEGIN
    DBMS_STATS.GATHER_TABLE_STATS(
        ownname => USER,
        tabname => 'AUTH_REFRESH_SESSION',
        cascade => TRUE
    );
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM user_tables
     WHERE table_name = 'AUTH_REFRESH_SESSION'
       AND tablespace_name = 'COMMON_DATA';

    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20985, 'AUTH_REFRESH_SESSION이 COMMON_DATA에 있지 않습니다.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_indexes
     WHERE table_name = 'AUTH_REFRESH_SESSION'
       AND index_name IN (
           'PK_AUTH_REFRESH_SESSION',
           'IX_ARS_EMPLOYEE',
           'IX_ARS_EXPIRY'
       )
       AND tablespace_name = 'COMMON_INDEX'
       AND status = 'VALID';

    IF v_count <> 3 THEN
        RAISE_APPLICATION_ERROR(-20986, 'AUTH_REFRESH_SESSION index가 COMMON_INDEX에 정규화되지 않았습니다.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_tables
     WHERE table_name = 'RESOURCE_REFRESH_SESSION';

    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20987, 'RESOURCE_REFRESH_SESSION 구 테이블이 남아 있습니다.');
    END IF;
END;
/

PROMPT COMMON AUTH REFRESH SESSION migration: PASS
COMMIT;
