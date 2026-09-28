package com.dyinfotech.annualleavebackend.repository.projection;

public record TeamCacheRow(
        Long teamId,
        String teamName,
        Long departmentId,
        Boolean enabled
) {
}
