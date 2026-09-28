package com.dyinfotech.annualleavebackend.repository.projection;

public record DepartmentCacheRow(
        Long departmentId,
        String departmentName,
        Boolean enabled
) {
}
