package com.dyinfotech.annualleavebackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findAllByEnabledTrue();

    Optional<Department> findByDepartmentName(String departmentName);

    Optional<Department> findByDepartmentNameAndEnabledTrue(String departmentName);

    @Query("select new com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow("
            + "d.departmentId, d.departmentName, d.enabled) "
            + "from Department d where d.enabled = true")
    List<DepartmentCacheRow> findAllEnabledForCache();

    @Query("select new com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow("
            + "d.departmentId, d.departmentName, d.enabled) "
            + "from Department d where d.departmentName = :departmentName and d.enabled = true")
    Optional<DepartmentCacheRow> findByNameEnabledForCache(@Param("departmentName") String departmentName);
}
