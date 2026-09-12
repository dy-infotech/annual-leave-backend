package com.dyinfotech.annualleavebackend.repository.projection;

import java.time.LocalDate;

public record LeaveUsage(Long employeeId, LocalDate startDate, Float useDays) {

}
