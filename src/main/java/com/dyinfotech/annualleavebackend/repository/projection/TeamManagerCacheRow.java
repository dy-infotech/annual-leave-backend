package com.dyinfotech.annualleavebackend.repository.projection;

import java.time.LocalDate;

public record TeamManagerCacheRow(
        Long teamId,
        Long projectManagerId,
        Long parentTeamId,
        String employeeNumber,
        String managerName,
        String position,
        LocalDate fireDate
) {
    public boolean isActive(LocalDate today) {
        return fireDate == null || !fireDate.isBefore(today);
    }
}
