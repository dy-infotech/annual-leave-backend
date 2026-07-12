INSERT INTO employee (employee_number, name, department, team, position, hire_date, curr_year, curr_total_leave_days)
VALUES
    ('A2025015', '이서우', 'SI사업팀', '스마트팩토리구축사업', '사원', '2025-08-06', YEAR(SYSDATE()), 15.0),
    ('A2020001', '이호영', 'SI사업팀', '스마트팩토리구축사업', '이사', '2020-03-15', YEAR(SYSDATE()), 15.0)
    ;

INSERT INTO team (team, project_manager_id, parent_team)
SELECT '스마트팩토리구축사업', employee_id, '대표이사' FROM employee WHERE name = '이호영';

INSERT INTO basis_data (year, seq, type, data, remark)
VALUES
	(YEAR(SYSDATE()), 1, 1, '15', '1년차 연차일수'),
	(YEAR(SYSDATE()), 2, 1, '2', 'N년당 추가연차 발생'),
	(YEAR(SYSDATE()), 3, 1, '1', '추가연차 일수'),
	(YEAR(SYSDATE()), 4, 3, '80.0', '만근 출석 퍼센트'),
	(YEAR(SYSDATE()), 5, 5, 'A#{YEAR}', '사번 접두사'),
	(YEAR(SYSDATE()), 6, 1, '25', '최대 연차일수')
	(YEAR(SYSDATA()), 7, 5, 'http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService', '한국천문연구원_특일 정보 API 서비스 URL')
	(YEAR(SYSDATA()), 8, 5, 'getRestDeInfo', '한국천문연구원_특일 정보 API 공휴일 요청 주소')
	;