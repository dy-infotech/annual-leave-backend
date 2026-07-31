package com.dyinfotech.annualleavebackend.repository.query;

import java.util.List;

import com.dyinfotech.annualleavebackend.domain.Employee;

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
}
