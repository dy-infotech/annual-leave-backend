package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;

class CurrentAuthorityServiceRegressionTest {

    private static final Long EMPLOYEE_ID = 7L;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void ceoCanViewAllLeaveDetailsWithoutPmRole() {
        Fixture fixture = fixture("사장", false);

        assertTrue(fixture.service().canViewAllLeaveDetails(EMPLOYEE_ID));
    }

    @Test
    void directorCanViewAllLeaveDetailsOnlyWhenCurrentPm() {
        Fixture pm = fixture("이사", true);
        Fixture nonPm = fixture("이사", false);

        assertTrue(pm.service().canViewAllLeaveDetails(EMPLOYEE_ID));
        assertFalse(nonPm.service().canViewAllLeaveDetails(EMPLOYEE_ID));
    }

    @Test
    void managerBelowDirectorCannotViewAllLeaveDetailsEvenWhenPm() {
        Fixture fixture = fixture("부장", true);

        assertFalse(fixture.service().canViewAllLeaveDetails(EMPLOYEE_ID));
    }

    private Fixture fixture(String position, boolean pm) {
        Employee employee = mock(Employee.class);
        when(employee.isActive(LocalDate.now(CLOCK))).thenReturn(true);
        when(employee.getPosition()).thenReturn(position);

        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        TeamService teamService = mock(TeamService.class);
        when(teamService.isTeamManagerFromDatabase(EMPLOYEE_ID)).thenReturn(pm);

        return new Fixture(
                new CurrentAuthorityService(employeeRepository, teamService, CLOCK));
    }

    private record Fixture(CurrentAuthorityService service) {
    }
}
