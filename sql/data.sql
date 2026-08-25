-- migration for prev version tables
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

-- Team 이관 결과 검증
SELECT
    (SELECT COUNT(DISTINCT team)
       FROM team_legacy) AS legacy_team_count,

    (SELECT COUNT(*)
       FROM team) AS migrated_team_count;

ALTER TABLE employee ADD COLUMN team_id BIGINT NULL COMMENT '팀 인덱스' AFTER team;

UPDATE employee e
JOIN team t
  ON e.team = t.team_name
SET e.team_id = t.team_id;

-- Employee Team 매핑 누락 검증. 아래 select문의 결과값이 0건이어야 함. 그 이후 추가 진행.
SELECT
    employee_id,
    employee_number,
    team
FROM employee
WHERE team_id IS NULL;

ALTER TABLE employee MODIFY COLUMN team_id BIGINT NOT NULL COMMENT '팀 인덱스';
ALTER TABLE employee ADD CONSTRAINT fk_employee_team FOREIGN KEY (team_id) REFERENCES team(team_id);
ALTER TABLE employee DROP COLUMN team;

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

-- 결과값 확인. 문제 있는지 확인부터 하고 legacy 테이블 삭제할 것.
-- 두 개수 일치해야함
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
-- Employee 전체가 Team에 정상 연결되어 있는지
SELECT COUNT(*) AS unmapped_employee_count
FROM employee e
LEFT JOIN team t
    ON t.team_id = e.team_id
WHERE t.team_id IS NULL;
-- TeamManager의 모든 Team FK가 정상인지
SELECT COUNT(*) AS invalid_team_manager_team_count
FROM team_manager tm
LEFT JOIN team t
    ON t.team_id = tm.team_id
WHERE t.team_id IS NULL;
-- TeamManager의 모든 Parent Team FK가 정상인지
SELECT COUNT(*) AS invalid_parent_team_count
FROM team_manager tm
LEFT JOIN team t
    ON t.team_id = tm.parent_team_id
WHERE t.team_id IS NULL;
-- TeamManager의 모든 Project Manager FK가 정상인지
SELECT COUNT(*) AS invalid_project_manager_count
FROM team_manager tm
LEFT JOIN employee e
    ON e.employee_id = tm.project_manager_id
WHERE e.employee_id IS NULL;

-- 최종 삭제
DROP TABLE team_legacy;

-- 데이터 마이그레이션시 반드시 approver_id를 not null로 설정해야 함
UPDATE employee SET approver_id = (SELECT employee_id FROM (SELECT employee_id FROM employee WHERE name = '우동영') AS TEMP) WHERE approver_id IS NULL;
ALTER TABLE employee MODIFY approver_id BIGINT NOT NULL;

-- 부서 처리
ALTER TABLE employee ADD COLUMN department_id BIGINT NULL COMMENT '부서 인덱스' AFTER department;

UPDATE employee e
JOIN department d
  ON e.department = d.department_name
SET e.department_id = d.department_id;

-- Employee Department 매핑 누락 검증. 아래 select문의 결과값이 0건이어야 함. 그 이후 추가 진행.
SELECT
    employee_id,
    employee_number,
    department
FROM employee
WHERE department_id IS NULL;

ALTER TABLE employee MODIFY COLUMN department_id BIGINT NOT NULL COMMENT '부서 인덱스';
ALTER TABLE employee ADD CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES department(department_id);
ALTER TABLE employee DROP COLUMN department;






-- curr version data
ALTER TABLE employee MODIFY approver_id BIGINT NULL;
INSERT INTO employee (employee_number, name, department, team, email, position, hire_date, curr_year, curr_total_leave_days, created_at, created_ip, updated_at, updated_ip)
VALUES
    ('A2011001', '우동영', 'SI사업팀', '대표이사', 'test@test.com', '사장', '2011-01-01', YEAR(SYSDATE()), 22.0, SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
    ('A2025016', '최민지', 'SI사업팀', '스마트팩토리구축사업', 'test@test.com', '사원', '2025-08-04', YEAR(SYSDATE()), 15.0, SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
    ('A2025015', '이서우', 'SI사업팀', '스마트팩토리구축사업', 'test@test.com', '사원', '2025-08-06', YEAR(SYSDATE()), 15.0, SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
    ('A2020001', '이호영', 'SI사업팀', '스마트팩토리구축사업', 'test@test.com', '이사', '2020-03-15', YEAR(SYSDATE()), 15.0, SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM')
    ;
UPDATE employee SET approver_id = (SELECT employee_id FROM (SELECT employee_id FROM employee WHERE name = '우동영') AS TEMP) WHERE approver_id IS NULL;
ALTER TABLE employee MODIFY approver_id BIGINT NOT NULL;

INSERT INTO department (department_name, enabled)
VALUES
	('대표이사', TRUE),
	('SI사업팀', TRUE)
	;

INSERT INTO team (team_name, enabled)
VALUES
	('대표이사', TRUE),
	('스마트팩토리구축사업', TRUE)
	;

INSERT INTO team_manager (team_id, project_manager_id, parent_team_id)
SELECT t1.team_id, e.employee_id, t2.team_id
FROM employee e
JOIN team t1 on t1.team_name = '스마트팩토리구축사업'
JOIN team t2 on t2.team_name = '대표이사'
WHERE e.name = '이호영';
INSERT INTO team_manager (team_id, project_manager_id, parent_team_id)
SELECT t.team_id, e.employee_id, t.team_id
FROM employee e
JOIN team t on t.team_name = '대표이사'
WHERE e.name = '우동영';

INSERT INTO basis_data (year, seq, type, data, remark)
VALUES
	(YEAR(SYSDATE()), 1, 1, '15', '1년차 연차일수'),
	(YEAR(SYSDATE()), 2, 1, '2', 'N년당 추가연차 발생'),
	(YEAR(SYSDATE()), 3, 1, '1', '추가연차 일수'),
	(YEAR(SYSDATE()), 4, 3, '80.0', '만근 출석 퍼센트'),
	(YEAR(SYSDATE()), 5, 5, 'A#{YEAR}', '사번 접두사'),
	(YEAR(SYSDATE()), 6, 1, '25', '최대 연차일수'),
	(YEAR(SYSDATE()), 7, 5, 'https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService', '한국천문연구원_특일 정보 API 서비스 URL'),
	(YEAR(SYSDATE()), 8, 5, 'getRestDeInfo', '한국천문연구원_특일 정보 API 공휴일 요청 주소'),
	(YEAR(SYSDATE()), 9, 1, '30', '로그인 실패 최대 횟수'),
	(YEAR(SYSDATE()), 10, 1, '24', '로그인 실패 잠금 해제까지 남은 시각'),
	(YEAR(SYSDATE()), 11, 0, 'false', '회계연도 정책 사용 여부')
	;
	
