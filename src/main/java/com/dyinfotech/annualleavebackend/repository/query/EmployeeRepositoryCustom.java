package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDateTime;
import java.util.List;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.projection.EmployeeNumberEmail;

public interface EmployeeRepositoryCustom {
    // 사용자 정보 조건 조회
	List<Employee> findAllEmployees(String searchParam, String team);
	default List<Employee> findAllEmployees(String searchParam) {
		return findAllEmployees(searchParam, null);
	}
	// 사용자 정보 전체 조회(파라미터 없는 경우)
	default List<Employee> findAllEmployees() {
		return findAllEmployees(null, null);
	}
	// 로그인 실패시 접근 횟수 추가 (@Transactional을 국소적으로 사용해야 하므로 JPA가 아니라 QueryDSL을 이용해서 DB에 반영하도록 수정했다. JPA로 마이그레이션 금지.)
	long increaseAccessCount(Long employeeId, LocalDateTime now);
	// 로그인 성공시 접근 횟수 초기화 (@Transactional을 국소적으로 사용해야 하므로 JPA가 아니라 QueryDSL을 이용해서 DB에 반영하도록 수정했다. JPA로 마이그레이션 금지.)
    long resetAccessCount(Long employeeId, LocalDateTime now);
    // 로그인 성공시 평문 패스워드를 암호화 (@Transactional을 국소적으로 사용해야 하므로 JPA가 아니라 QueryDSL을 이용해서 DB에 반영하도록 수정했다. JPA로 마이그레이션 금지.)
    long updatePassword(Long employeeId, String password);
    // 로그인 성공시 올해 총 연차 수 업데이트 (@Transactional을 국소적으로 사용해야 하므로 JPA가 아니라 QueryDSL을 이용해서 DB에 반영하도록 수정했다. JPA로 마이그레이션 금지.)
    long updateCurrTotalLeaveDays(Long employeeId, float days);
	// 이름으로 이메일 조회
    List<String> findEmailsByName(String name);
    // 사원명으로 이메일 조회
    String findEmailsByEmployeeNumber(String employeeName);
    // 이름과 이메일로 사번과 이메일 조회
    List<EmployeeNumberEmail> findEmployeeNumberAndEmailByNameAndEmailIn(String name, List<String> emailList);
}
