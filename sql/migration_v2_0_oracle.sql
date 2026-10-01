-- =====================================================================
-- annual-leave develop_v1.0 -> develop_v2.0
-- Oracle 21c XE organization schema migration
--
-- Legacy:
--   EMPLOYEE.DEPARTMENT / EMPLOYEE.TEAM : VARCHAR2
--   TEAM(SEQ, TEAM, PROJECT_MANAGER_ID, PARENT_TEAM)
--
-- Target:
--   DEPARTMENT(DEPARTMENT_ID, DEPARTMENT_NAME, ENABLED)
--   TEAM(TEAM_ID, TEAM_NAME, DEPARTMENT_ID, ENABLED)
--   TEAM_MANAGER(TEAM_ID, PROJECT_MANAGER_ID, PARENT_TEAM_ID)
--   EMPLOYEE.DEPARTMENT_ID / EMPLOYEE.TEAM_ID
--
-- IMPORTANT
-- 1) SQL*Plus / SQLcl 기준 스크립트다.
-- 2) Oracle DDL은 implicit commit이므로 실행 전 반드시 DB 백업을 수행한다.
-- 3) TEAM_LEGACY와 EMPLOYEE_ORG_LEGACY는 애플리케이션 검증이 끝날 때까지 삭제하지 않는다.
-- 4) 기존 팀에 서로 다른 부서의 사원이 섞여 있으면 임의로 다수결하지 않고
--    사전 검증에서 중단한다.
-- 5) Oracle DDL은 중간 rollback이 불가능하므로 DDL 시작 후 실패한 스크립트를 그대로 재실행하지 않는다.
--    TEAM_LEGACY / EMPLOYEE_ORG_LEGACY와 외부 백업을 기준으로 수동 복구 후 다시 실행한다.
-- =====================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK;

SET SERVEROUTPUT ON;

PROMPT [1/9] Precheck legacy organization data

DECLARE
    v_count NUMBER;
BEGIN
    -- 필수 문자열 누락
    SELECT COUNT(*)
      INTO v_count
      FROM employee
     WHERE TRIM(team) IS NULL
        OR TRIM(department) IS NULL;

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20001,
            'EMPLOYEE의 TEAM/DEPARTMENT 누락 데이터가 존재합니다. count=' || v_count
        );
    END IF;

    -- 하나의 팀에 여러 부서가 섞여 있으면 신규 Team.department_id를 안전하게 결정할 수 없다.
    SELECT COUNT(*)
      INTO v_count
      FROM (
            SELECT TRIM(e.team)
              FROM employee e
             WHERE TRIM(e.team) <> '대표이사'
             GROUP BY TRIM(e.team)
            HAVING COUNT(DISTINCT TRIM(e.department)) > 1
      );

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20002,
            '동일 팀에 복수 부서가 존재합니다. 팀-부서 매핑을 먼저 정리하세요. team_count=' || v_count
        );
    END IF;

    -- 기존 TeamManager PK가 될 조합 중복 방어
    SELECT COUNT(*)
      INTO v_count
      FROM (
            SELECT TRIM(t.team), t.project_manager_id
              FROM team t
             GROUP BY TRIM(t.team), t.project_manager_id
            HAVING COUNT(*) > 1
      );

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20003,
            'TEAM에 중복된 (TEAM, PROJECT_MANAGER_ID)가 존재합니다. count=' || v_count
        );
    END IF;

    -- parent_team이 실제 팀으로도 존재하는지 확인한다.
    SELECT COUNT(*)
      INTO v_count
      FROM (
            SELECT DISTINCT TRIM(t.parent_team)
              FROM team t
             WHERE TRIM(t.parent_team) IS NOT NULL
               AND NOT EXISTS (
                    SELECT 1
                      FROM team p
                     WHERE TRIM(p.team) = TRIM(t.parent_team)
             )
               AND NOT EXISTS (
                    SELECT 1
                      FROM employee e
                     WHERE TRIM(e.team) = TRIM(t.parent_team)
             )
      );

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20004,
            '상위 팀 문자열이 실제 팀/사원 소속 팀에 존재하지 않습니다. count=' || v_count
        );
    END IF;

    -- 사원이 없는 팀은 기존 PM의 부서로만 부서를 추론한다.
    -- 그 PM들의 부서가 여러 개면 역시 자동 결정하지 않는다.
    SELECT COUNT(*)
      INTO v_count
      FROM (
            SELECT TRIM(t.team)
              FROM team t
              JOIN employee pm
                ON pm.employee_id = t.project_manager_id
             WHERE TRIM(t.team) <> '대표이사'
               AND NOT EXISTS (
                    SELECT 1
                      FROM employee e
                     WHERE TRIM(e.team) = TRIM(t.team)
               )
             GROUP BY TRIM(t.team)
            HAVING COUNT(DISTINCT TRIM(pm.department)) > 1
      );

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20005,
            '사원이 없는 팀의 PM 부서가 서로 달라 팀 부서를 자동 결정할 수 없습니다. count=' || v_count
        );
    END IF;

    -- 동일 팀의 PM row들이 서로 다른 상위 팀을 가리키면 신규 모델의 단일 계층을 결정할 수 없다.
    SELECT COUNT(*)
      INTO v_count
      FROM (
            SELECT TRIM(t.team)
              FROM team t
             GROUP BY TRIM(t.team)
            HAVING COUNT(DISTINCT TRIM(t.parent_team)) > 1
      );

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20012,
            '동일 팀의 상위 팀 정보가 PM별로 다릅니다. team_count=' || v_count
        );
    END IF;

    -- self-parent(root)는 허용하되 길이 2 이상의 조직 순환은 DDL 전에 차단한다.
    SELECT COUNT(*)
      INTO v_count
      FROM (
            SELECT CONNECT_BY_ISCYCLE AS is_cycle
              FROM (
                    SELECT DISTINCT TRIM(team) AS team_name,
                                    TRIM(parent_team) AS parent_team_name
                      FROM team
                     WHERE TRIM(team) <> TRIM(parent_team)
              )
             START WITH team_name IS NOT NULL
            CONNECT BY NOCYCLE PRIOR parent_team_name = team_name
      )
     WHERE is_cycle = 1;

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20013,
            '조직 계층에 순환 참조가 존재합니다. cycle_count=' || v_count
        );
    END IF;

    -- 재직 사원이 있거나 하위 팀이 의존하는 팀에는 재직 PM이 최소 1명 필요하다.
    SELECT COUNT(*)
      INTO v_count
      FROM (
            SELECT required.team_name
              FROM (
                    SELECT DISTINCT TRIM(e.team) AS team_name
                      FROM employee e
                     WHERE e.hire_date <= TRUNC(SYSDATE)
                       AND (e.fire_date IS NULL OR e.fire_date >= TRUNC(SYSDATE))
                    UNION
                    SELECT DISTINCT TRIM(t.parent_team)
                      FROM team t
                     WHERE TRIM(t.parent_team) <> TRIM(t.team)
              ) required
             WHERE NOT EXISTS (
                    SELECT 1
                      FROM team tm
                      JOIN employee pm
                        ON pm.employee_id = tm.project_manager_id
                     WHERE TRIM(tm.team) = required.team_name
                       AND pm.hire_date <= TRUNC(SYSDATE)
                       AND (pm.fire_date IS NULL OR pm.fire_date >= TRUNC(SYSDATE))
             )
      );

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20014,
            '재직 사원 또는 하위 팀이 의존하지만 재직 PM이 없는 팀이 존재합니다. team_count=' || v_count
        );
    END IF;
END;
/

PROMPT [2/9] Preserve legacy organization data

CREATE TABLE employee_org_legacy AS
SELECT employee_id,
       department AS department_name,
       team AS team_name
  FROM employee;

ALTER TABLE team RENAME TO team_legacy;

PROMPT [3/9] Create DEPARTMENT

CREATE TABLE department (
    department_id   NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
    department_name VARCHAR2(50 CHAR) NOT NULL,
    enabled         NUMBER(1) DEFAULT 1 NOT NULL,

    CONSTRAINT pk_department PRIMARY KEY (department_id),
    CONSTRAINT uk_dept_name UNIQUE (department_name),
    CONSTRAINT ck_dept_enabled CHECK (enabled IN (0, 1))
);

INSERT INTO department (department_name, enabled)
SELECT DISTINCT TRIM(e.department), 1
  FROM employee e
 WHERE TRIM(e.department) IS NOT NULL;

-- DepartmentType의 최상위 부서는 기존 데이터에 없어도 생성한다.
MERGE INTO department d
USING (SELECT '대표이사' AS department_name FROM dual) src
   ON (d.department_name = src.department_name)
WHEN NOT MATCHED THEN
    INSERT (department_name, enabled)
    VALUES (src.department_name, 1);

PROMPT [4/9] Create normalized TEAM

CREATE TABLE team (
    team_id             NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
    team_name           VARCHAR2(30 CHAR) NOT NULL,
    department_id       NUMBER(19) NOT NULL,
    enabled             NUMBER(1) DEFAULT 1 NOT NULL,
    create_request_key  VARCHAR2(128 CHAR),
    create_request_hash VARCHAR2(64 CHAR),

    CONSTRAINT pk_team PRIMARY KEY (team_id),
    CONSTRAINT uk_team_name UNIQUE (team_name),
    CONSTRAINT uk_team_create_request UNIQUE (create_request_key),
    CONSTRAINT ck_team_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_team_create_request_pair CHECK (
        (create_request_key IS NULL AND create_request_hash IS NULL)
        OR
        (create_request_key IS NOT NULL AND create_request_hash IS NOT NULL)
    ),
    CONSTRAINT fk_team_dept
        FOREIGN KEY (department_id)
        REFERENCES department(department_id)
);

-- 팀 목록은 legacy TEAM + parent team + 실제 사원 소속 팀의 합집합이다.
-- 부서 결정 우선순위:
--   1) 대표이사 팀 -> 대표이사 부서
--   2) 해당 팀 소속 사원의 부서
--   3) 소속 사원이 없는 legacy 팀 -> 해당 팀 PM의 부서
INSERT INTO team (team_name, department_id, enabled)
WITH team_names AS (
    SELECT TRIM(team) AS team_name
      FROM team_legacy
    UNION
    SELECT TRIM(parent_team)
      FROM team_legacy
    UNION
    SELECT TRIM(team)
      FROM employee
),
team_department_map AS (
    SELECT n.team_name,
           CASE
               WHEN n.team_name = '대표이사' THEN '대표이사'
               ELSE COALESCE(
                    (
                        SELECT MIN(TRIM(e.department))
                          FROM employee e
                         WHERE TRIM(e.team) = n.team_name
                    ),
                    (
                        SELECT MIN(TRIM(pm.department))
                          FROM team_legacy tl
                          JOIN employee pm
                            ON pm.employee_id = tl.project_manager_id
                         WHERE TRIM(tl.team) = n.team_name
                    )
               )
           END AS department_name
      FROM team_names n
)
SELECT m.team_name,
       d.department_id,
       1
  FROM team_department_map m
  JOIN department d
    ON d.department_name = m.department_name;

-- 모든 legacy/employee 팀명이 신규 TEAM으로 매핑됐는지 확인
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM (
            SELECT team_name
              FROM (
                    SELECT TRIM(team) AS team_name FROM team_legacy
                    UNION
                    SELECT TRIM(parent_team) FROM team_legacy
                    UNION
                    SELECT TRIM(team) FROM employee
              )
            MINUS
            SELECT team_name FROM team
      );

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20006,
            '신규 TEAM으로 매핑되지 않은 팀명이 존재합니다. count=' || v_count
        );
    END IF;
END;
/

PROMPT [5/9] Create TEAM_MANAGER and migrate approval hierarchy

CREATE TABLE team_manager (
    team_id            NUMBER(19) NOT NULL,
    project_manager_id NUMBER(19) NOT NULL,
    parent_team_id     NUMBER(19) NOT NULL,

    CONSTRAINT pk_team_manager
        PRIMARY KEY (team_id, project_manager_id),
    CONSTRAINT fk_tm_team
        FOREIGN KEY (team_id)
        REFERENCES team(team_id),
    CONSTRAINT fk_tm_parent
        FOREIGN KEY (parent_team_id)
        REFERENCES team(team_id),
    CONSTRAINT fk_tm_pm
        FOREIGN KEY (project_manager_id)
        REFERENCES employee(employee_id)
);

INSERT INTO team_manager (
    team_id,
    project_manager_id,
    parent_team_id
)
SELECT t.team_id,
       tl.project_manager_id,
       pt.team_id
  FROM team_legacy tl
  JOIN team t
    ON t.team_name = TRIM(tl.team)
  JOIN team pt
    ON pt.team_name = TRIM(tl.parent_team);

DECLARE
    v_legacy_count NUMBER;
    v_new_count    NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_legacy_count FROM team_legacy;
    SELECT COUNT(*) INTO v_new_count FROM team_manager;

    IF v_legacy_count <> v_new_count THEN
        RAISE_APPLICATION_ERROR(
            -20007,
            'TEAM_MANAGER 이관 건수가 legacy TEAM과 다릅니다. legacy='
            || v_legacy_count || ', migrated=' || v_new_count
        );
    END IF;
END;
/

PROMPT [6/9] Add EMPLOYEE foreign keys

ALTER TABLE employee ADD (
    department_id NUMBER(19),
    team_id       NUMBER(19)
);

-- 사원의 부서는 신규 TEAM.department_id를 기준으로 맞춘다.
-- 기존 모델에서 대표이사 팀/부서 명칭이 달랐던 경우도 여기서 신규 불변식에 맞춰진다.
UPDATE employee e
   SET (team_id, department_id) = (
        SELECT t.team_id, t.department_id
          FROM team t
         WHERE t.team_name = TRIM(e.team)
   );

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM employee
     WHERE team_id IS NULL
        OR department_id IS NULL;

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20008,
            'EMPLOYEE의 신규 TEAM/DEPARTMENT FK 매핑 누락이 존재합니다. count=' || v_count
        );
    END IF;
END;
/

ALTER TABLE employee MODIFY (
    department_id NOT NULL,
    team_id       NOT NULL
);

ALTER TABLE employee ADD CONSTRAINT fk_emp_dept
    FOREIGN KEY (department_id)
    REFERENCES department(department_id);

ALTER TABLE employee ADD CONSTRAINT fk_emp_team
    FOREIGN KEY (team_id)
    REFERENCES team(team_id);

PROMPT [7/9] Remove legacy EMPLOYEE string columns

ALTER TABLE employee DROP COLUMN department;
ALTER TABLE employee DROP COLUMN team;

PROMPT [7.5/9] Create v2 query indexes

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
        'IX_EMPLOYEE_TEAM',
        'CREATE INDEX ix_employee_team ON employee(team_id)');
    ensure_index(
        'IX_EMPLOYEE_DEPARTMENT',
        'CREATE INDEX ix_employee_department ON employee(department_id)');
    ensure_index(
        'IX_TEAM_DEPARTMENT',
        'CREATE INDEX ix_team_department ON team(department_id)');
    ensure_index(
        'IX_LEAVE_REQUEST_EMPLOYEE',
        'CREATE INDEX ix_leave_request_employee ON leave_request(employee_id)');
    ensure_index(
        'IX_LEAVE_REQUEST_STATUS_DATES',
        'CREATE INDEX ix_leave_request_status_dates ON leave_request(status, start_date, end_date)');
    ensure_index(
        'IX_TEAM_MANAGER_PARENT',
        'CREATE INDEX ix_team_manager_parent ON team_manager(parent_team_id)');
    ensure_index(
        'IX_TEAM_MANAGER_PROJECT_MANAGER',
        'CREATE INDEX ix_team_manager_project_manager ON team_manager(project_manager_id)');
END;
/

PROMPT [8/9] Final validation

DECLARE
    v_count NUMBER;
BEGIN
    -- Employee FK 무결성
    SELECT COUNT(*)
      INTO v_count
      FROM employee e
      LEFT JOIN team t
        ON t.team_id = e.team_id
      LEFT JOIN department d
        ON d.department_id = e.department_id
     WHERE t.team_id IS NULL
        OR d.department_id IS NULL;

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20009,
            'EMPLOYEE 조직 FK 무결성 검증 실패. count=' || v_count
        );
    END IF;

    -- Team.department와 Employee.department 불변식
    SELECT COUNT(*)
      INTO v_count
      FROM employee e
      JOIN team t
        ON t.team_id = e.team_id
     WHERE e.department_id <> t.department_id;

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20010,
            'EMPLOYEE.department_id != TEAM.department_id 데이터가 존재합니다. count=' || v_count
        );
    END IF;

    -- TeamManager FK 무결성
    SELECT COUNT(*)
      INTO v_count
      FROM team_manager tm
      LEFT JOIN team t
        ON t.team_id = tm.team_id
      LEFT JOIN team pt
        ON pt.team_id = tm.parent_team_id
      LEFT JOIN employee pm
        ON pm.employee_id = tm.project_manager_id
     WHERE t.team_id IS NULL
        OR pt.team_id IS NULL
        OR pm.employee_id IS NULL;

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20011,
            'TEAM_MANAGER FK 무결성 검증 실패. count=' || v_count
        );
    END IF;

    -- 한 팀의 관리자들은 동일한 parent_team_id를 사용해야 한다.
    SELECT COUNT(*)
      INTO v_count
      FROM (
            SELECT team_id
              FROM team_manager
             GROUP BY team_id
            HAVING COUNT(DISTINCT parent_team_id) > 1
      );

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20015,
            'TEAM_MANAGER의 상위 팀 정보가 관리자별로 다릅니다. team_count=' || v_count
        );
    END IF;

    -- self-parent(root)를 제외한 조직 순환 검증
    SELECT COUNT(*)
      INTO v_count
      FROM (
            SELECT CONNECT_BY_ISCYCLE AS is_cycle
              FROM (
                    SELECT DISTINCT team_id, parent_team_id
                      FROM team_manager
                     WHERE team_id <> parent_team_id
              )
             START WITH team_id IS NOT NULL
            CONNECT BY NOCYCLE PRIOR parent_team_id = team_id
      )
     WHERE is_cycle = 1;

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20016,
            '신규 조직 계층에 순환 참조가 존재합니다. cycle_count=' || v_count
        );
    END IF;

    -- 재직 사원 또는 하위 팀이 의존하는 팀은 재직 PM을 가져야 한다.
    SELECT COUNT(*)
      INTO v_count
      FROM (
            SELECT required.team_id
              FROM (
                    SELECT DISTINCT e.team_id
                      FROM employee e
                     WHERE e.hire_date <= TRUNC(SYSDATE)
                       AND (e.fire_date IS NULL OR e.fire_date >= TRUNC(SYSDATE))
                    UNION
                    SELECT DISTINCT tm.parent_team_id
                      FROM team_manager tm
                     WHERE tm.parent_team_id <> tm.team_id
              ) required
             WHERE NOT EXISTS (
                    SELECT 1
                      FROM team_manager tm
                      JOIN employee pm
                        ON pm.employee_id = tm.project_manager_id
                     WHERE tm.team_id = required.team_id
                       AND pm.hire_date <= TRUNC(SYSDATE)
                       AND (pm.fire_date IS NULL OR pm.fire_date >= TRUNC(SYSDATE))
             )
      );

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20017,
            '재직 사원 또는 하위 팀이 의존하지만 재직 PM이 없는 신규 팀이 존재합니다. team_count=' || v_count
        );
    END IF;
END;
/

PROMPT [8.25/9] Create DB-level PM fire-date guard

-- =====================================================================
-- DB-level guard: PM 퇴사일 직접 UPDATE로 결재 공백이 생기는 것을 차단한다.
--
-- Row trigger에서 EMPLOYEE를 다시 조회하면 ORA-04091(mutating table)이 발생할 수
-- 있으므로 compound trigger의 AFTER STATEMENT 단계에서 최종 상태를 검증한다.
-- 한 UPDATE문에서 여러 PM의 FIRE_DATE를 동시에 바꾸는 경우도 최종 상태 기준으로
-- 함께 검증된다.
-- =====================================================================
CREATE OR REPLACE TRIGGER trg_employee_pm_fire_date_guard
FOR UPDATE OF fire_date ON employee
COMPOUND TRIGGER

    TYPE t_employee_id_list IS TABLE OF NUMBER(19) INDEX BY PLS_INTEGER;
    g_employee_ids t_employee_id_list;
    g_employee_count PLS_INTEGER := 0;

    AFTER EACH ROW IS
    BEGIN
        -- 퇴사일 제거(NULL)는 재활성화 방향이므로 차단 대상이 아니다.
        IF :NEW.fire_date IS NOT NULL
           AND (:OLD.fire_date IS NULL OR :OLD.fire_date <> :NEW.fire_date) THEN
            g_employee_count := g_employee_count + 1;
            g_employee_ids(g_employee_count) := :NEW.employee_id;
        END IF;
    END AFTER EACH ROW;

    AFTER STATEMENT IS
        v_fire_date      DATE;
        v_inactive_from  DATE;
        v_check_date     DATE;
        v_gap_team_count NUMBER;
    BEGIN
        FOR i IN 1 .. g_employee_count LOOP
            SELECT fire_date
              INTO v_fire_date
              FROM employee
             WHERE employee_id = g_employee_ids(i);

            IF v_fire_date IS NULL THEN
                CONTINUE;
            END IF;

            -- LocalDate.MAX(9999-12-31)는 +1 시 Oracle DATE 범위를 벗어날 수 있다.
            IF v_fire_date >= DATE '9999-12-31' THEN
                v_inactive_from := v_fire_date;
            ELSE
                v_inactive_from := v_fire_date + 1;
            END IF;

            -- 과거 일자로 소급 퇴사 처리하더라도 현재 조직에 결재 공백이 생기면 막는다.
            v_check_date := GREATEST(v_inactive_from, TRUNC(SYSDATE));

            SELECT COUNT(DISTINCT tm.team_id)
              INTO v_gap_team_count
              FROM team_manager tm
             WHERE tm.project_manager_id = g_employee_ids(i)
               AND (
                    EXISTS (
                        SELECT 1
                          FROM employee dependent_employee
                         WHERE dependent_employee.team_id = tm.team_id
                           AND dependent_employee.employee_id <> g_employee_ids(i)
                           AND dependent_employee.hire_date <= v_check_date
                           AND (
                                dependent_employee.fire_date IS NULL
                                OR dependent_employee.fire_date >= v_check_date
                           )
                    )
                    OR EXISTS (
                        SELECT 1
                          FROM team_manager child_team
                         WHERE child_team.parent_team_id = tm.team_id
                           AND child_team.team_id <> tm.team_id
                    )
               )
               AND NOT EXISTS (
                    SELECT 1
                      FROM team_manager other_manager
                      JOIN employee other_pm
                        ON other_pm.employee_id = other_manager.project_manager_id
                     WHERE other_manager.team_id = tm.team_id
                       AND other_manager.project_manager_id <> g_employee_ids(i)
                       AND other_pm.hire_date <= v_check_date
                       AND (
                            other_pm.fire_date IS NULL
                            OR other_pm.fire_date >= v_check_date
                       )
               );

            IF v_gap_team_count > 0 THEN
                RAISE_APPLICATION_ERROR(
                    -20031,
                    'PM 퇴사 처리로 결재 공백이 발생합니다. 다른 재직 PM을 먼저 지정하세요. employee_id='
                    || g_employee_ids(i)
                    || ', team_count='
                    || v_gap_team_count
                );
            END IF;
        END LOOP;
    END AFTER STATEMENT;

END trg_employee_pm_fire_date_guard;
/

PROMPT [8.5/9] Create password reset token store

CREATE TABLE password_reset_token (
    token_id     NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
    employee_id  NUMBER(19) NOT NULL,
    token_hash   VARCHAR2(64 CHAR) NOT NULL,
    expires_at   TIMESTAMP(6) NOT NULL,
    consumed_at  TIMESTAMP(6),
    created_at   TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_password_reset_token PRIMARY KEY (token_id),
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_employee
        FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
);

CREATE INDEX ix_password_reset_employee ON password_reset_token(employee_id);

PROMPT [9/9] Migration completed

SELECT COUNT(*) AS department_count FROM department;
SELECT COUNT(*) AS team_count FROM team;
SELECT COUNT(*) AS team_manager_count FROM team_manager;
SELECT COUNT(*) AS employee_count FROM employee;

-- 상세 확인
SELECT t.team_id,
       t.team_name,
       d.department_name,
       t.enabled
  FROM team t
  JOIN department d
    ON d.department_id = t.department_id
 ORDER BY t.team_name;

SELECT t.team_name,
       tm.project_manager_id,
       pm.name AS project_manager_name,
       pt.team_name AS parent_team_name
  FROM team_manager tm
  JOIN team t
    ON t.team_id = tm.team_id
  JOIN employee pm
    ON pm.employee_id = tm.project_manager_id
  JOIN team pt
    ON pt.team_id = tm.parent_team_id
 ORDER BY t.team_name, pm.name;

-- ---------------------------------------------------------------------
-- 검증이 끝난 뒤에만 legacy 테이블을 수동 삭제한다.
-- 즉시 삭제하지 않는 이유: Oracle DDL은 implicit commit이므로 운영 검증 전
-- rollback 자료를 남겨두기 위함이다.
--
-- DROP TABLE team_legacy PURGE;
-- DROP TABLE employee_org_legacy PURGE;
-- ---------------------------------------------------------------------
