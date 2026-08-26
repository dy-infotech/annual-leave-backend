-- =============================================================
-- 구버전(legacy) DB → 신규 스키마 마이그레이션
-- 대상: employee.department/team이 문자열 컬럼이고
--       team 테이블이 (seq, team, project_manager_id, parent_team) 형태인 DB
-- 실행 전 반드시 mysqldump로 백업할 것.
-- 중간의 검증 SELECT 결과가 어긋나면 아래 NOT NULL 전환에서 실패하도록 구성되어 있다.
-- =============================================================
USE annual_leave;

-- -------------------------------------------------------------
-- 1. 팀 마이그레이션: 기존 team을 team_legacy로 보존 후 신규 team 생성
-- -------------------------------------------------------------
RENAME TABLE team TO team_legacy;
ALTER TABLE team_legacy
    DROP FOREIGN KEY fk_project_manager,
    ADD CONSTRAINT fk_team_legacy_project_manager
        FOREIGN KEY (project_manager_id)
        REFERENCES employee(employee_id);

CREATE TABLE team (
    team_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_name VARCHAR(30) NOT NULL COMMENT '팀명',
    enabled   TINYINT(1) NOT NULL DEFAULT TRUE COMMENT '팀 활성 여부',

    CONSTRAINT uk_team_name UNIQUE KEY (team_name)
) COMMENT '팀 정보';

INSERT INTO team (team_name, enabled)
SELECT DISTINCT
       team,
       TRUE
FROM team_legacy;

-- Team 이관 결과 검증: 두 개수가 일치해야 함
SELECT
    (SELECT COUNT(DISTINCT team)
       FROM team_legacy) AS legacy_team_count,

    (SELECT COUNT(*)
       FROM team) AS migrated_team_count;

-- employee.team(문자열) → team_id(FK) 전환
ALTER TABLE employee ADD COLUMN team_id BIGINT NULL COMMENT '팀 인덱스' AFTER team;

UPDATE employee e
JOIN team t
  ON e.team = t.team_name
SET e.team_id = t.team_id;

-- Employee Team 매핑 누락 검증: 0건이어야 함 (누락 시 아래 NOT NULL 전환에서 실패)
SELECT
    employee_id,
    employee_number,
    team
FROM employee
WHERE team_id IS NULL;

ALTER TABLE employee MODIFY COLUMN team_id BIGINT NOT NULL COMMENT '팀 인덱스';
ALTER TABLE employee ADD CONSTRAINT fk_employee_team FOREIGN KEY (team_id) REFERENCES team(team_id);
ALTER TABLE employee DROP COLUMN team;

-- -------------------------------------------------------------
-- 2. team_manager 생성 및 이관
-- -------------------------------------------------------------
CREATE TABLE team_manager (
    team_id            BIGINT NOT NULL COMMENT '팀 인덱스',
    project_manager_id BIGINT NOT NULL COMMENT '프로젝트 담당자',
    parent_team_id     BIGINT NOT NULL COMMENT '상위 팀 인덱스',

    PRIMARY KEY (team_id, project_manager_id),

    CONSTRAINT fk_team
        FOREIGN KEY (team_id)
        REFERENCES team(team_id),
    CONSTRAINT fk_parent_team
        FOREIGN KEY (parent_team_id)
        REFERENCES team(team_id),
    CONSTRAINT fk_project_manager
        FOREIGN KEY (project_manager_id)
        REFERENCES employee(employee_id)
) COMMENT '팀 매니저 정보 (결재라인 상급자 탐색용)';

INSERT INTO team_manager (
    team_id,
    project_manager_id,
    parent_team_id
)
SELECT
    t.team_id,
    tl.project_manager_id,
    pt.team_id
FROM team_legacy tl
JOIN team t
  ON t.team_name = tl.team
JOIN team pt
  ON pt.team_name = tl.parent_team;

-- 이관 결과 검증: 두 개수가 일치해야 함
SELECT
    (SELECT COUNT(*)
       FROM team_legacy) AS legacy_team_manager_count,

    (SELECT COUNT(*)
       FROM team_manager) AS migrated_team_manager_count;

-- 상세 결과 확인
SELECT
    tm.team_id,
    t.team_name,
    tm.project_manager_id,
    e.name AS project_manager_name,
    tm.parent_team_id,
    pt.team_name AS parent_team_name
FROM team_manager tm
JOIN team t
    ON t.team_id = tm.team_id
JOIN employee e
    ON e.employee_id = tm.project_manager_id
JOIN team pt
    ON pt.team_id = tm.parent_team_id
ORDER BY t.team_name, e.name;

-- Employee 전체가 Team에 정상 연결되어 있는지 (0건이어야 함)
SELECT COUNT(*) AS unmapped_employee_count
FROM employee e
LEFT JOIN team t
    ON t.team_id = e.team_id
WHERE t.team_id IS NULL;
-- TeamManager의 모든 Team FK가 정상인지 (0건이어야 함)
SELECT COUNT(*) AS invalid_team_manager_team_count
FROM team_manager tm
LEFT JOIN team t
    ON t.team_id = tm.team_id
WHERE t.team_id IS NULL;
-- TeamManager의 모든 Parent Team FK가 정상인지 (0건이어야 함)
SELECT COUNT(*) AS invalid_parent_team_count
FROM team_manager tm
LEFT JOIN team t
    ON t.team_id = tm.parent_team_id
WHERE t.team_id IS NULL;
-- TeamManager의 모든 Project Manager FK가 정상인지 (0건이어야 함)
SELECT COUNT(*) AS invalid_project_manager_count
FROM team_manager tm
LEFT JOIN employee e
    ON e.employee_id = tm.project_manager_id
WHERE e.employee_id IS NULL;

-- 최종 삭제
DROP TABLE team_legacy;

-- -------------------------------------------------------------
-- 3. approver_id NOT NULL 전환 (누락분은 대표이사로 지정)
-- -------------------------------------------------------------
UPDATE employee SET approver_id = (SELECT employee_id FROM (SELECT employee_id FROM employee WHERE name = '우동영') AS TEMP) WHERE approver_id IS NULL;
ALTER TABLE employee MODIFY approver_id BIGINT NOT NULL COMMENT '승인한 관리자 번호';

-- -------------------------------------------------------------
-- 4. 부서 마이그레이션: department 테이블 생성 및 employee 전환
-- -------------------------------------------------------------
CREATE TABLE department (
    department_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    department_name VARCHAR(50) NOT NULL COMMENT '부서명',
    enabled         TINYINT(1) NOT NULL DEFAULT TRUE COMMENT '활성 여부',

    CONSTRAINT uk_department_name UNIQUE KEY (department_name)
) COMMENT '부서 정보';

-- 재직자의 부서 + 코드(DepartmentType)가 요구하는 '대표이사' 부서 등록
INSERT INTO department (department_name, enabled)
SELECT DISTINCT department, TRUE
FROM employee
WHERE department IS NOT NULL;

INSERT IGNORE INTO department (department_name, enabled) VALUES ('대표이사', TRUE);

ALTER TABLE employee ADD COLUMN department_id BIGINT NULL COMMENT '부서 인덱스' AFTER department;

UPDATE employee e
JOIN department d
  ON e.department = d.department_name
SET e.department_id = d.department_id;

-- Employee Department 매핑 누락 검증: 0건이어야 함 (누락 시 아래 NOT NULL 전환에서 실패)
SELECT
    employee_id,
    employee_number,
    department
FROM employee
WHERE department_id IS NULL;

ALTER TABLE employee MODIFY COLUMN department_id BIGINT NOT NULL COMMENT '부서 인덱스';
ALTER TABLE employee ADD CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES department(department_id);
ALTER TABLE employee DROP COLUMN department;
