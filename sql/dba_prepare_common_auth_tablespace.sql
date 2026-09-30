-- =====================================================================
-- DBA 전용: 연차/자산관리 공통 인증 세션 tablespace 준비
--
-- Oracle XE에서 SYSDBA로 접속하면 CDB$ROOT일 수 있다.
-- HR_USER가 있는 XEPDB1 PDB인지 먼저 확인한다.
--
--   SHOW CON_NAME;
--   ALTER SESSION SET CONTAINER = XEPDB1;
--
-- DATAFILE 경로는 Oracle 서버/컨테이너 내부 경로다.
-- 기존 HR_DATA/RSC_DATA의 실제 경로를 확인한 뒤 같은 디렉터리를 사용한다.
--
--   SELECT tablespace_name, file_name, bytes/1024/1024 AS mb
--     FROM dba_data_files
--    WHERE tablespace_name IN ('HR_DATA', 'HR_INDEX', 'RSC_DATA', 'RSC_INDEX');
--
-- 실제 데이터파일 경로는 운영 Oracle 환경에 맞게 수정한 뒤 SYSDBA 또는
-- CREATE TABLESPACE 권한이 있는 계정으로 1회 실행한다.
-- =====================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK;

CREATE TABLESPACE COMMON_DATA
    DATAFILE '/opt/oracle/oradata/XE/XEPDB1/COMMON_DATA01.dbf' SIZE 200M
    AUTOEXTEND ON NEXT 50M MAXSIZE UNLIMITED
    EXTENT MANAGEMENT LOCAL
    SEGMENT SPACE MANAGEMENT AUTO;

CREATE TABLESPACE COMMON_INDEX
    DATAFILE '/opt/oracle/oradata/XE/XEPDB1/COMMON_INDEX01.dbf' SIZE 100M
    AUTOEXTEND ON NEXT 25M MAXSIZE UNLIMITED
    EXTENT MANAGEMENT LOCAL
    SEGMENT SPACE MANAGEMENT AUTO;

ALTER USER HR_USER QUOTA UNLIMITED ON COMMON_DATA;
ALTER USER HR_USER QUOTA UNLIMITED ON COMMON_INDEX;
