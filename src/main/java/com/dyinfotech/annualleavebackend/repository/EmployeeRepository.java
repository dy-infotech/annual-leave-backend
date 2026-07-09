package com.dyinfotech.annualleavebackend.repository;

import com.dyinfotech.annualleavebackend.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmployeeNo(String employeeNo);
    @Deprecated
    Optional<Employee> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
}