package com.dyinfotech.annualleavebackend.repository.projection;

import java.time.LocalDate;

public record TeamManagerCacheRow(
        Long teamId,
        Long projectManagerId,
        Long parentTeamId,
        String employeeNumber,
        String managerName,
        String position,
        LocalDate hireDate,
        LocalDate fireDate
) {
    public boolean isActive(LocalDate date) {
        return !hireDate.isAfter(date)
                && (fireDate == null || !fireDate.isBefore(date));
    }
}
