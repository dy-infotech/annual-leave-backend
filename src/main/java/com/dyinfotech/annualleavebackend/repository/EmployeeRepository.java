package com.dyinfotech.annualleavebackend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmployeeNumber(String employeeNumber);
//    @Deprecated
//    Optional<Employee> findByLoginId(String loginId);
//    @Deprecated
//    boolean existsByLoginId(String loginId);
    
    Optional<Employee> findFirstByEmployeeNumberStartingWithOrderByEmployeeNumberDesc(String prefix);
    
    List<Employee> findAllByEmployeeIdIn(Collection<Long> employeeIds);
    
    List<Employee> findAllByFireDateIsNull();
    
    Optional<Employee> findByEmployeeNumberAndEmail(String employeeNumber, String email);
}