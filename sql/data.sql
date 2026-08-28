-- =============================================================
-- 시드 데이터 (신규 스키마 전용)
-- schema.sql로 새로 생성한 DB에만 실행한다.
-- 구버전(legacy) DB를 전환하는 경우 migration.sql을 사용할 것.
-- =============================================================
USE annual_leave;

-- 부서
INSERT INTO department (department_name, enabled)
VALUES
	('대표이사', TRUE),
	('SI사업팀', TRUE)
	;

-- 팀 (부서:팀 = 1:N — 소속 부서 필수)
INSERT INTO team (team_name, department_id, enabled)
SELECT t.team_name, d.department_id, TRUE
FROM (
	          SELECT '대표이사' AS team_name, '대표이사' AS department_name
	UNION ALL SELECT '스마트팩토리구축사업', 'SI사업팀'
) t
JOIN department d ON d.department_name = t.department_name;

-- 사원
-- approver_id가 자기참조 NOT NULL이라 초기 등록이 불가능하므로 임시로 NULL 허용 후 지정한다.
ALTER TABLE employee MODIFY approver_id BIGINT NULL COMMENT '승인한 관리자 번호';

INSERT INTO employee (employee_number, name, department_id, team_id, email, position, hire_date, curr_year, curr_total_leave_days, created_at, created_ip, updated_at, updated_ip)
SELECT e.employee_number, e.name, d.department_id, t.team_id, e.email, e.position, e.hire_date, YEAR(SYSDATE()), e.leave_days, SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'
FROM (
	          SELECT 'A2011001' AS employee_number, '우동영' AS name, '대표이사' AS department, '대표이사' AS team, 'test@test.com' AS email, '사장' AS position, '2011-01-01' AS hire_date, 22.0 AS leave_days
	UNION ALL SELECT 'A2020001', '이호영', 'SI사업팀', '스마트팩토리구축사업', 'test@test.com', '이사', '2020-03-15', 15.0
	UNION ALL SELECT 'A2025016', '최민지', 'SI사업팀', '스마트팩토리구축사업', 'test@test.com', '사원', '2025-08-04', 15.0
	UNION ALL SELECT 'A2025015', '이서우', 'SI사업팀', '스마트팩토리구축사업', 'test@test.com', '사원', '2025-08-06', 15.0
) e
JOIN department d ON d.department_name = e.department
JOIN team t ON t.team_name = e.team;

-- 승인자 지정: 팀원은 팀 담당자(PM), 담당자와 대표이사는 대표이사 본인
UPDATE employee e
JOIN employee pm ON pm.name = '이호영'
SET e.approver_id = pm.employee_id
WHERE e.name IN ('최민지', '이서우');

UPDATE employee e
JOIN employee ceo ON ceo.name = '우동영'
SET e.approver_id = ceo.employee_id
WHERE e.approver_id IS NULL;

ALTER TABLE employee MODIFY approver_id BIGINT NOT NULL COMMENT '승인한 관리자 번호';

-- 팀 담당자 (결재라인)
-- 스마트팩토리구축사업: PM 이호영, 상위 팀 대표이사
INSERT INTO team_manager (team_id, project_manager_id, parent_team_id)
SELECT t1.team_id, e.employee_id, t2.team_id
FROM employee e
JOIN team t1 ON t1.team_name = '스마트팩토리구축사업'
JOIN team t2 ON t2.team_name = '대표이사'
WHERE e.name = '이호영';

-- 대표이사(루트 팀): PM 우동영, 상위 팀은 자기 자신
INSERT INTO team_manager (team_id, project_manager_id, parent_team_id)
SELECT t.team_id, e.employee_id, t.team_id
FROM employee e
JOIN team t ON t.team_name = '대표이사'
WHERE e.name = '우동영';

-- 기초데이터
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
