-- =====================================================================
-- annual-leave develop_v2.0 Oracle 21c XE seed data
--
-- Run after sql/schema.sql on a fresh database.
-- Existing v1 databases must run sql/migration_v2_0_oracle.sql first.
-- This script is intentionally idempotent for the fixed development seed.
-- =====================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK;

-- Basis-data API URLs are longer than the legacy 50-char column.
ALTER TABLE basis_data MODIFY (data VARCHAR2(255 CHAR));

MERGE INTO department d
USING (
    SELECT 910001 department_id, '대표이사' department_name FROM dual
    UNION ALL
    SELECT 910002, 'SI사업팀' FROM dual
) src
ON (d.department_name = src.department_name)
WHEN MATCHED THEN UPDATE SET d.enabled = 1
WHEN NOT MATCHED THEN INSERT (department_id, department_name, enabled)
VALUES (src.department_id, src.department_name, 1);

MERGE INTO team t
USING (
    SELECT 920001 team_id, '대표이사' team_name, '대표이사' department_name FROM dual
    UNION ALL
    SELECT 920002, '스마트팩토리구축사업', 'SI사업팀' FROM dual
) src
ON (t.team_name = src.team_name)
WHEN MATCHED THEN UPDATE SET
    t.department_id = (SELECT d.department_id FROM department d WHERE d.department_name = src.department_name),
    t.enabled = 1
WHEN NOT MATCHED THEN INSERT (team_id, team_name, department_id, enabled)
VALUES (
    src.team_id,
    src.team_name,
    (SELECT d.department_id FROM department d WHERE d.department_name = src.department_name),
    1
);

MERGE INTO employee e
USING (
    SELECT 930001 employee_id, 'A2011001' employee_number, '우동영' name,
           '대표이사' department_name, '대표이사' team_name, 'test@test.com' email,
           '사장' position, DATE '2011-01-01' hire_date, 22.0 leave_days, 930001 approver_id
      FROM dual
    UNION ALL
    SELECT 930002, 'A2020001', '이호영',
           'SI사업팀', '스마트팩토리구축사업', 'test@test.com',
           '이사', DATE '2020-03-15', 15.0, 930001
      FROM dual
    UNION ALL
    SELECT 930003, 'A2025016', '최민지',
           'SI사업팀', '스마트팩토리구축사업', 'test@test.com',
           '사원', DATE '2025-08-04', 15.0, 930002
      FROM dual
    UNION ALL
    SELECT 930004, 'A2025015', '이서우',
           'SI사업팀', '스마트팩토리구축사업', 'test@test.com',
           '사원', DATE '2025-08-06', 15.0, 930002
      FROM dual
) src
ON (e.employee_number = src.employee_number)
WHEN MATCHED THEN UPDATE SET
    e.name = src.name,
    e.department_id = (SELECT d.department_id FROM department d WHERE d.department_name = src.department_name),
    e.team_id = (SELECT t.team_id FROM team t WHERE t.team_name = src.team_name),
    e.position = src.position,
    e.email = src.email,
    e.curr_year = TO_CHAR(SYSDATE, 'YYYY'),
    e.curr_total_leave_days = src.leave_days,
    e.hire_date = src.hire_date,
    e.approver_id = src.approver_id,
    e.updated_at = SYSTIMESTAMP,
    e.updated_ip = 'SYSTEM'
WHEN NOT MATCHED THEN INSERT (
    employee_id, employee_number, name, department_id, team_id, email, position,
    hire_date, curr_year, curr_total_leave_days, approver_id,
    created_at, created_ip, updated_at, updated_ip
) VALUES (
    src.employee_id, src.employee_number, src.name,
    (SELECT d.department_id FROM department d WHERE d.department_name = src.department_name),
    (SELECT t.team_id FROM team t WHERE t.team_name = src.team_name),
    src.email, src.position, src.hire_date, TO_CHAR(SYSDATE, 'YYYY'),
    src.leave_days, src.approver_id,
    SYSTIMESTAMP, 'SYSTEM', SYSTIMESTAMP, 'SYSTEM'
);

MERGE INTO team_manager tm
USING (
    SELECT 920001 team_id, 930001 project_manager_id, 920001 parent_team_id FROM dual
    UNION ALL
    SELECT 920002, 930002, 920001 FROM dual
) src
ON (tm.team_id = src.team_id AND tm.project_manager_id = src.project_manager_id)
WHEN MATCHED THEN UPDATE SET tm.parent_team_id = src.parent_team_id
WHEN NOT MATCHED THEN INSERT (team_id, project_manager_id, parent_team_id)
VALUES (src.team_id, src.project_manager_id, src.parent_team_id);

MERGE INTO basis_data b
USING (
    SELECT TO_CHAR(SYSDATE, 'YYYY') year, 1 seq, '1' type, '15' data, '1년차 연차일수' remark FROM dual
    UNION ALL SELECT TO_CHAR(SYSDATE, 'YYYY'), 2, '1', '2', 'N년당 추가연차 발생' FROM dual
    UNION ALL SELECT TO_CHAR(SYSDATE, 'YYYY'), 3, '1', '1', '추가연차 일수' FROM dual
    UNION ALL SELECT TO_CHAR(SYSDATE, 'YYYY'), 4, '3', '80.0', '만근 출석 퍼센트' FROM dual
    UNION ALL SELECT TO_CHAR(SYSDATE, 'YYYY'), 5, '5', 'A#{YEAR}', '사번 접두사' FROM dual
    UNION ALL SELECT TO_CHAR(SYSDATE, 'YYYY'), 6, '1', '25', '최대 연차일수' FROM dual
    UNION ALL SELECT TO_CHAR(SYSDATE, 'YYYY'), 7, '5', 'https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService', '한국천문연구원_특일 정보 API 서비스 URL' FROM dual
    UNION ALL SELECT TO_CHAR(SYSDATE, 'YYYY'), 8, '5', 'getRestDeInfo', '한국천문연구원_특일 정보 API 공휴일 요청 주소' FROM dual
    UNION ALL SELECT TO_CHAR(SYSDATE, 'YYYY'), 9, '1', '30', '로그인 실패 최대 횟수' FROM dual
    UNION ALL SELECT TO_CHAR(SYSDATE, 'YYYY'), 10, '1', '24', '로그인 실패 잠금 해제까지 남은 시각' FROM dual
    UNION ALL SELECT TO_CHAR(SYSDATE, 'YYYY'), 11, '0', 'false', '회계연도 정책 사용 여부' FROM dual
) src
ON (b.year = src.year AND b.seq = src.seq)
WHEN MATCHED THEN UPDATE SET b.type = src.type, b.data = src.data, b.remark = src.remark
WHEN NOT MATCHED THEN INSERT (year, seq, type, data, remark)
VALUES (src.year, src.seq, src.type, src.data, src.remark);

COMMIT;
