package com.dyinfotech.annualleavebackend.repository.query;

import java.util.List;
import java.util.Optional;

import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;

public interface DepartmentRepositoryCustom {
    List<DepartmentCacheRow> findAllEnabledForCache();
    Optional<DepartmentCacheRow> findByNameEnabledForCache(String departmentName);
    Optional<Department> findByIdForUpdate(Long departmentId);
}
