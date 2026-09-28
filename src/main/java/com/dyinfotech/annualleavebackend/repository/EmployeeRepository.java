package com.dyinfotech.annualleavebackend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.query.EmployeeRepositoryCustom;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, EmployeeRepositoryCustom {
    Optional<Employee> findByEmployeeNumber(String employeeNumber);
    Optional<Employee> findFirstByEmployeeNumberStartingWithOrderByEmployeeNumberDesc(String prefix);
    List<Employee> findAllByEmployeeIdInOrderByEmployeeIdAsc(Collection<Long> employeeIds);
    List<Employee> findAllByTeam_TeamId(Long teamId);

    Optional<Employee> findByEmployeeNumberAndEmail(String employeeNumber, String email);
}
