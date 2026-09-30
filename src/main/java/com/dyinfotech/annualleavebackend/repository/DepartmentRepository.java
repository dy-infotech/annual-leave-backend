package com.dyinfotech.annualleavebackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.repository.query.DepartmentRepositoryCustom;

public interface DepartmentRepository extends JpaRepository<Department, Long>, DepartmentRepositoryCustom {

    List<Department> findAllByEnabledTrue();

    Optional<Department> findByDepartmentName(String departmentName);

    Optional<Department> findByDepartmentNameAndEnabledTrue(String departmentName);
}
