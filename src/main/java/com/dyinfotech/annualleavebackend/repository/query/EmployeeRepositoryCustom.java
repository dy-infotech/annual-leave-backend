package com.dyinfotech.annualleavebackend.repository.query;

import java.util.List;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.projection.EmployeeNumberEmail;

public interface EmployeeRepositoryCustom {
    //사용자 정보 조건 조회
	List<Employee> findAllEmployees(String searchParam, String team);
	default List<Employee> findAllEmployees(String searchParam) {
		return findAllEmployees(searchParam, null);
	}
	//사용자 정보 전체 조회(파라미터 없는 경우)
	default List<Employee> findAllEmployees() {
		return findAllEmployees(null, null);
	}
	// 이름으로 이메일 조회
    List<String> findEmailsByName(String name);
    // 사원명으로 이메일 조회
    List<String> findEmailsByEmployeeNumber(String employeeName);
    // 이름과 이메일로 사번과 이메일 조회
    List<EmployeeNumberEmail> findEmployeeNumberAndEmailByNameAndEmailIn(String name, List<String> emailList);
}
