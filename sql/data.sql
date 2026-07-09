INSERT INTO employee (employee_number, name, department, team, position, hire_date, curr_year, curr_total_leave_days)
VALUES
    ('A2025015', '이서우', 'SI사업팀', '스마트팩토리구축사업', '사원', '2025-08-06', YEAR(SYSDATE()), 15.0),
    ('A2020001', '이호영', 'SI사업팀', '스마트팩토리구축사업', '이사', '2020-03-15', YEAR(SYSDATE()), 15.0);

INSERT INTO team (team, project_manager_id, parent_team)
VALUES
	('스마트팩토리구축사업', 2, '대표이사');