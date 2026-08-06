-- migration for prev version tables
ALTER TABLE employee MODIFY COLUMN accessed_at DATETIME NULL COMMENT '로그인 실패 시각';
ALTER TABLE employee ADD COLUMN accessed_ip VARCHAR(45) NULL COMMENT '로그인 실패 IP 주소' AFTER accessed_at;
ALTER TABLE employee MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '생성 시각';
ALTER TABLE employee ADD COLUMN created_ip VARCHAR(45) NOT NULL DEFAULT 'SYSTEM' COMMENT '생성 요청 IP 주소' AFTER created_at;
ALTER TABLE employee MODIFY COLUMN updated_at DATETIME NOT NULL COMMENT '수정 시각';
ALTER TABLE employee ADD COLUMN updated_ip VARCHAR(45) NOT NULL DEFAULT 'SYSTEM' COMMENT '수정 요청 IP 주소' AFTER updated_at;

ALTER TABLE leave_request MODIFY COLUMN managed_at DATETIME NULL COMMENT '처리 시각';
ALTER TABLE leave_request ADD COLUMN managed_ip VARCHAR(45) NULL DEFAULT 'SYSTEM' COMMENT '처리 요청 IP 주소' AFTER managed_at;
ALTER TABLE leave_request MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '생성 시각';
ALTER TABLE leave_request ADD COLUMN created_ip VARCHAR(45) NOT NULL DEFAULT 'SYSTEM' COMMENT '생성 요청 IP 주소' AFTER created_at;

ALTER TABLE leave_adjustment MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '생성 시각';
ALTER TABLE leave_adjustment ADD COLUMN created_ip VARCHAR(45) NOT NULL DEFAULT 'SYSTEM' COMMENT '생성 요청 IP 주소' AFTER created_at;
ALTER TABLE leave_adjustment MODIFY COLUMN updated_at DATETIME NOT NULL COMMENT '수정 시각';
ALTER TABLE leave_adjustment ADD COLUMN updated_ip VARCHAR(45) NOT NULL DEFAULT 'SYSTEM' COMMENT '수정 요청 IP 주소' AFTER updated_at;

ALTER TABLE fcm_token MODIFY COLUMN updated_at DATETIME NOT NULL COMMENT '수정 시각';
ALTER TABLE fcm_token ADD COLUMN updated_ip VARCHAR(45) NOT NULL DEFAULT 'SYSTEM' COMMENT '수정 요청 IP 주소' AFTER updated_at;

ALTER TABLE team ADD CONSTRAINT uk_team_project_manager UNIQUE KEY (team, project_manager_id);



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

INSERT INTO team (team, project_manager_id, parent_team)
SELECT '스마트팩토리구축사업', employee_id, '대표이사' FROM employee WHERE name = '이호영';
INSERT INTO team (team, project_manager_id, parent_team)
SELECT '대표이사', employee_id, '대표이사' FROM employee WHERE name = '우동영';

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
	(YEAR(SYSDATE()), 10, 1, '24', '로그인 실패 잠금 해제까지 남은 시각')
	(YEAR(SYSDATE()), 11, 0, 'false', '회계연도 정책 사용 여부')
	;
	
