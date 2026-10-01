package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CurrentAuthorityServiceTest {

    private static final Long EMPLOYEE_ID = 7L;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);

    private EmployeeRepository employeeRepository;
    private TeamService teamService;
    private CurrentAuthorityService service;

    @BeforeEach
    void setUp() {
        employeeRepository = mock(EmployeeRepository.class);
        teamService = mock(TeamService.class);
        service = new CurrentAuthorityService(employeeRepository, teamService, CLOCK);
    }

    @Test
    void isAdmin_usesCurrentDbPmAuthority() {
        Employee employee = activeEmployee("부장");
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(teamService.isTeamManagerFromDatabase(EMPLOYEE_ID)).thenReturn(true);

        assertTrue(service.isAdmin(EMPLOYEE_ID));
    }

    @Test
    void isAdmin_revokedPmIsRejectedEvenIfDisplayCacheCouldBeStale() {
        Employee employee = activeEmployee("부장");
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(teamService.isTeamManagerFromDatabase(EMPLOYEE_ID)).thenReturn(false);
        when(teamService.isTeamManager(EMPLOYEE_ID)).thenReturn(true);

        assertFalse(service.isAdmin(EMPLOYEE_ID));
    }

    @Test
    void canViewAllLeaveDetails_ceoDoesNotRequirePm() {
        Employee employee = activeEmployee("대표이사");
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(teamService.isTeamManagerFromDatabase(EMPLOYEE_ID)).thenReturn(false);

        assertTrue(service.canViewAllLeaveDetails(EMPLOYEE_ID));
    }

    @Test
    void canViewAllLeaveDetails_directorRequiresPm() {
        Employee employee = activeEmployee("이사");
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(teamService.isTeamManagerFromDatabase(EMPLOYEE_ID)).thenReturn(false);

        assertFalse(service.canViewAllLeaveDetails(EMPLOYEE_ID));

        when(teamService.isTeamManagerFromDatabase(EMPLOYEE_ID)).thenReturn(true);
        assertTrue(service.canViewAllLeaveDetails(EMPLOYEE_ID));
    }

    @Test
    void canViewAllLeaveDetails_pmBelowDirectorIsNotGlobal() {
        Employee employee = activeEmployee("부장");
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(teamService.isTeamManagerFromDatabase(EMPLOYEE_ID)).thenReturn(true);

        assertFalse(service.canViewAllLeaveDetails(EMPLOYEE_ID));
    }

    @Test
    void canViewAllLeaveDetails_missingPositionIsSafelyNonGlobal() {
        Employee employee = activeEmployee(null);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(teamService.isTeamManagerFromDatabase(EMPLOYEE_ID)).thenReturn(true);

        assertFalse(service.canViewAllLeaveDetails(EMPLOYEE_ID));
        assertFalse(service.isCeo(EMPLOYEE_ID));
    }

    @Test
    void canViewAllLeaveDetails_inactiveExecutiveIsNotGlobal() {
        Employee employee = mock(Employee.class);
        when(employee.isActive(LocalDate.now(CLOCK))).thenReturn(false);
        when(employee.getPosition()).thenReturn("전무");
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        assertFalse(service.canViewAllLeaveDetails(EMPLOYEE_ID));
    }

    private Employee activeEmployee(String position) {
        Employee employee = mock(Employee.class);
        when(employee.isActive(LocalDate.now(CLOCK))).thenReturn(true);
        when(employee.getPosition()).thenReturn(position);
        return employee;
    }
}
