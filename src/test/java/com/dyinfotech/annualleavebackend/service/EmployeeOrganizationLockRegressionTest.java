package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.cache.OrganizationCacheInvalidator;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.TeamManagerRepository;
import com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow;

class EmployeeOrganizationLockRegressionTest {

    @Test
    void employeeAdminUpdate_prelocksWholeTeamSetBeforeEmployeeAndRoleChanges() {
        TeamService teamService = mock(TeamService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        CommonService commonService = mock(CommonService.class);
        EmployeeLeaveService employeeLeaveService = mock(EmployeeLeaveService.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        TeamManagerRepository teamManagerRepository = mock(TeamManagerRepository.class);
        OrganizationCacheInvalidator cacheInvalidator = mock(OrganizationCacheInvalidator.class);
        EmployeeCacheInvalidator employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        EmployeeService service = new EmployeeService(
                teamService,
                departmentService,
                commonService,
                employeeLeaveService,
                employeeRepository,
                teamManagerRepository,
                cacheInvalidator,
                employeeCacheInvalidator,
                passwordEncoder
        );

        Employee approver = mock(Employee.class);
        Employee employee = mock(Employee.class);
        Team currentTeam = mock(Team.class);
        Department department = mock(Department.class);
        EmployeeDto.EmployeeAdminUpdateRequest request = mock(EmployeeDto.EmployeeAdminUpdateRequest.class);

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(approver));
        when(approver.hasPersonnelAuthority()).thenReturn(true);
        when(approver.getTeamId()).thenReturn(30L);

        when(employeeRepository.findByEmployeeNumber("E001")).thenReturn(Optional.of(employee));
        when(employee.getEmployeeId()).thenReturn(1L);
        when(employee.getName()).thenReturn("직원");
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getTeam()).thenReturn(currentTeam);
        when(employee.getPosition()).thenReturn("사원");
        when(currentTeam.getTeamId()).thenReturn(10L);
        when(currentTeam.getDepartment()).thenReturn(department);
        when(department.getDepartmentId()).thenReturn(1L);

        when(request.getDepartment()).thenReturn("SI사업팀");
        when(request.getManagedTeams()).thenReturn(null);
        when(request.getTargetTeamsForRoleSwap()).thenReturn(List.of("T2", "T1"));
        when(request.getHireDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(departmentService.findByDepartmentName("SI사업팀")).thenReturn(Optional.of(department));

        TeamCacheRow t1 = new TeamCacheRow(10L, "T1", 1L, true);
        TeamCacheRow t2 = new TeamCacheRow(20L, "T2", 1L, true);
        when(teamService.findTeamInfo("T1")).thenReturn(Optional.of(t1));
        when(teamService.findTeamInfo("T2")).thenReturn(Optional.of(t2));
        when(teamService.findTeamInfo(10L)).thenReturn(Optional.of(t1));
        when(teamService.findTeamInfo(20L)).thenReturn(Optional.of(t2));
        when(teamService.resolveParentTeamId("T1")).thenReturn(Optional.of(30L));
        when(teamService.resolveParentTeamId("T2")).thenReturn(Optional.of(30L));

        when(teamManagerRepository.findTeamIdsByProjectManagerId(1L))
                .thenReturn(List.of(10L, 20L), List.of(10L, 20L), List.of(10L, 20L));
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));

        service.updateEmployeeByAdmin(100L, "E001", request);

        InOrder inOrder = inOrder(teamService, employeeRepository);
        inOrder.verify(teamService).lockTeamsForUpdate(argThat(teamIds ->
                teamIds.size() == 3
                        && teamIds.contains(10L)
                        && teamIds.contains(20L)
                        && teamIds.contains(30L)));
        inOrder.verify(employeeRepository).findByIdForUpdate(1L);
        inOrder.verify(teamService).removeManager(20L, 1L);
        inOrder.verify(teamService).removeManager(10L, 1L);
    }
    @Test
    void employeeAdminUpdate_teamMove_repairsStoredApproverImmediately() {
        TeamService teamService = mock(TeamService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        CommonService commonService = mock(CommonService.class);
        EmployeeLeaveService employeeLeaveService = mock(EmployeeLeaveService.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        TeamManagerRepository teamManagerRepository = mock(TeamManagerRepository.class);
        OrganizationCacheInvalidator cacheInvalidator = mock(OrganizationCacheInvalidator.class);
        EmployeeCacheInvalidator employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        EmployeeService service = new EmployeeService(
                teamService,
                departmentService,
                commonService,
                employeeLeaveService,
                employeeRepository,
                teamManagerRepository,
                cacheInvalidator,
                employeeCacheInvalidator,
                passwordEncoder
        );

        Employee approver = mock(Employee.class);
        Employee employee = mock(Employee.class);
        Team currentTeam = mock(Team.class);
        Team targetTeam = mock(Team.class);
        Department department = mock(Department.class);
        EmployeeDto.EmployeeAdminUpdateRequest request = mock(EmployeeDto.EmployeeAdminUpdateRequest.class);
        LocalDate hireDate = LocalDate.of(2024, 1, 1);

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(approver));
        when(approver.hasPersonnelAuthority()).thenReturn(true);

        when(employeeRepository.findByEmployeeNumber("E001")).thenReturn(Optional.of(employee));
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));
        when(employee.getEmployeeId()).thenReturn(1L);
        when(employee.getName()).thenReturn("직원");
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getTeam()).thenReturn(currentTeam);
        when(employee.getPosition()).thenReturn("사원");
        when(currentTeam.getTeamId()).thenReturn(10L);

        when(request.getDepartment()).thenReturn("개발부");
        when(request.getTeam()).thenReturn("ABC");
        when(request.getPosition()).thenReturn("사원");
        when(request.getHireDate()).thenReturn(hireDate);
        when(departmentService.findByDepartmentName("개발부")).thenReturn(Optional.of(department));

        TeamCacheRow targetTeamInfo = new TeamCacheRow(20L, "ABC", 1L, true);
        when(teamService.findTeamInfo("ABC")).thenReturn(Optional.of(targetTeamInfo));
        when(teamService.findByTeamName("ABC")).thenReturn(Optional.of(targetTeam));
        when(targetTeam.getTeamId()).thenReturn(20L);
        when(targetTeam.getDepartment()).thenReturn(department);
        when(department.getDepartmentId()).thenReturn(1L);
        when(teamManagerRepository.findTeamIdsByProjectManagerId(1L)).thenReturn(List.of());

        service.updateEmployeeByAdmin(100L, "E001", request);

        InOrder inOrder = inOrder(employee, teamService);
        inOrder.verify(employee).updateInfoByAdmin(
                any(), any(), any(), eq(targetTeam), any(), eq(hireDate), any(), any());
        inOrder.verify(teamService).refreshApproverIds(employee);
    }

    @Test
    void employeeAdminUpdate_sameDesiredManagedTeams_doesNotToggleRoles() {
        TeamService teamService = mock(TeamService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        CommonService commonService = mock(CommonService.class);
        EmployeeLeaveService employeeLeaveService = mock(EmployeeLeaveService.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        TeamManagerRepository teamManagerRepository = mock(TeamManagerRepository.class);
        OrganizationCacheInvalidator cacheInvalidator = mock(OrganizationCacheInvalidator.class);
        EmployeeCacheInvalidator employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        EmployeeService service = new EmployeeService(
                teamService, departmentService, commonService, employeeLeaveService,
                employeeRepository, teamManagerRepository, cacheInvalidator,
                employeeCacheInvalidator, passwordEncoder);

        Employee approver = mock(Employee.class);
        Employee employee = mock(Employee.class);
        Team currentTeam = mock(Team.class);
        Department department = mock(Department.class);
        EmployeeDto.EmployeeAdminUpdateRequest request = mock(EmployeeDto.EmployeeAdminUpdateRequest.class);

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(approver));
        when(approver.hasPersonnelAuthority()).thenReturn(true);
        when(approver.getTeamId()).thenReturn(30L);
        when(employeeRepository.findByEmployeeNumber("E001")).thenReturn(Optional.of(employee));
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));
        when(employee.getEmployeeId()).thenReturn(1L);
        when(employee.getName()).thenReturn("직원");
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getTeam()).thenReturn(currentTeam);
        when(employee.getPosition()).thenReturn("사원");
        when(currentTeam.getTeamId()).thenReturn(10L);
        when(currentTeam.getDepartment()).thenReturn(department);
        when(department.getDepartmentId()).thenReturn(1L);
        when(department.getDepartmentName()).thenReturn("SI사업팀");
        when(request.getDepartment()).thenReturn("SI사업팀");
        when(request.getManagedTeams()).thenReturn(List.of("T1", "T2"));
        when(request.getHireDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(departmentService.findByDepartmentName("SI사업팀")).thenReturn(Optional.of(department));

        TeamCacheRow t1 = new TeamCacheRow(10L, "T1", 1L, true);
        TeamCacheRow t2 = new TeamCacheRow(20L, "T2", 1L, true);
        when(teamService.findTeamInfo("T1")).thenReturn(Optional.of(t1));
        when(teamService.findTeamInfo("T2")).thenReturn(Optional.of(t2));
        when(teamService.findTeamInfo(10L)).thenReturn(Optional.of(t1));
        when(teamService.findTeamInfo(20L)).thenReturn(Optional.of(t2));
        when(teamService.resolveParentTeamId("T1")).thenReturn(Optional.of(30L));
        when(teamService.resolveParentTeamId("T2")).thenReturn(Optional.of(30L));
        when(teamManagerRepository.findTeamIdsByProjectManagerId(1L)).thenReturn(List.of(10L, 20L));

        service.updateEmployeeByAdmin(100L, "E001", request);

        verify(teamService, never()).removeManager(any(), any());
        verify(teamService, never()).addManager(any(), any(), any());
    }

    @Test
    void managedTeamsUpdate_replayedAfterCommit_isIdempotentNoOp() {
        TeamService teamService = mock(TeamService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        CommonService commonService = mock(CommonService.class);
        EmployeeLeaveService employeeLeaveService = mock(EmployeeLeaveService.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        TeamManagerRepository teamManagerRepository = mock(TeamManagerRepository.class);
        OrganizationCacheInvalidator cacheInvalidator = mock(OrganizationCacheInvalidator.class);
        EmployeeCacheInvalidator employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        EmployeeService service = new EmployeeService(
                teamService, departmentService, commonService, employeeLeaveService,
                employeeRepository, teamManagerRepository, cacheInvalidator,
                employeeCacheInvalidator, passwordEncoder);

        Employee approver = mock(Employee.class);
        Employee employee = mock(Employee.class);
        EmployeeDto.ManagedTeamsUpdateRequest request = mock(EmployeeDto.ManagedTeamsUpdateRequest.class);
        TeamCacheRow t1 = new TeamCacheRow(10L, "T1", 1L, true);
        TeamCacheRow t2 = new TeamCacheRow(20L, "T2", 1L, true);

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(approver));
        when(approver.hasPersonnelAuthority()).thenReturn(true);
        when(approver.getTeamId()).thenReturn(30L);
        when(employeeRepository.findByEmployeeNumber("E001")).thenReturn(Optional.of(employee));
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));
        when(employee.getEmployeeId()).thenReturn(1L);
        when(request.getExpectedManagedTeams()).thenReturn(List.of("T1"));
        when(request.getManagedTeams()).thenReturn(List.of("T1", "T2"));
        when(teamService.findTeamInfo("T1")).thenReturn(Optional.of(t1));
        when(teamService.findTeamInfo("T2")).thenReturn(Optional.of(t2));
        when(teamService.findTeamInfo(10L)).thenReturn(Optional.of(t1));
        when(teamService.findTeamInfo(20L)).thenReturn(Optional.of(t2));
        when(teamService.resolveParentTeamId("T1")).thenReturn(Optional.of(30L));
        when(teamService.resolveParentTeamId("T2")).thenReturn(Optional.of(30L));
        when(teamManagerRepository.findTeamIdsByProjectManagerId(1L))
                .thenReturn(List.of(10L, 20L), List.of(10L, 20L));

        service.updateManagedTeamsByAdmin(100L, "E001", request);

        verify(teamService, never()).removeManager(any(), any());
        verify(teamService, never()).addManager(any(), any(), any());
    }

    @Test
    void managedTeamsUpdate_staleExpectedState_isRejectedWithoutWrite() {
        TeamService teamService = mock(TeamService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        CommonService commonService = mock(CommonService.class);
        EmployeeLeaveService employeeLeaveService = mock(EmployeeLeaveService.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        TeamManagerRepository teamManagerRepository = mock(TeamManagerRepository.class);
        OrganizationCacheInvalidator cacheInvalidator = mock(OrganizationCacheInvalidator.class);
        EmployeeCacheInvalidator employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        EmployeeService service = new EmployeeService(
                teamService, departmentService, commonService, employeeLeaveService,
                employeeRepository, teamManagerRepository, cacheInvalidator,
                employeeCacheInvalidator, passwordEncoder);

        Employee approver = mock(Employee.class);
        Employee employee = mock(Employee.class);
        EmployeeDto.ManagedTeamsUpdateRequest request = mock(EmployeeDto.ManagedTeamsUpdateRequest.class);
        TeamCacheRow t1 = new TeamCacheRow(10L, "T1", 1L, true);
        TeamCacheRow t2 = new TeamCacheRow(20L, "T2", 1L, true);
        TeamCacheRow t3 = new TeamCacheRow(30L, "T3", 1L, true);

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(approver));
        when(approver.hasPersonnelAuthority()).thenReturn(true);
        when(approver.getTeamId()).thenReturn(40L);
        when(employeeRepository.findByEmployeeNumber("E001")).thenReturn(Optional.of(employee));
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));
        when(employee.getEmployeeId()).thenReturn(1L);
        when(request.getExpectedManagedTeams()).thenReturn(List.of("T1"));
        when(request.getManagedTeams()).thenReturn(List.of("T1", "T3"));
        when(teamService.findTeamInfo("T1")).thenReturn(Optional.of(t1));
        when(teamService.findTeamInfo("T3")).thenReturn(Optional.of(t3));
        when(teamService.findTeamInfo(10L)).thenReturn(Optional.of(t1));
        when(teamService.findTeamInfo(20L)).thenReturn(Optional.of(t2));
        when(teamService.resolveParentTeamId("T1")).thenReturn(Optional.of(40L));
        when(teamService.resolveParentTeamId("T3")).thenReturn(Optional.of(40L));
        when(teamManagerRepository.findTeamIdsByProjectManagerId(1L))
                .thenReturn(List.of(10L, 20L), List.of(10L, 20L));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateManagedTeamsByAdmin(100L, "E001", request));

        assertEquals(409, exception.getStatusCode().value());
        verify(teamService, never()).removeManager(any(), any());
        verify(teamService, never()).addManager(any(), any(), any());
    }

    @Test
    void managedTeamsUpdate_matchingExpectedState_appliesOnlyDiff() {
        TeamService teamService = mock(TeamService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        CommonService commonService = mock(CommonService.class);
        EmployeeLeaveService employeeLeaveService = mock(EmployeeLeaveService.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        TeamManagerRepository teamManagerRepository = mock(TeamManagerRepository.class);
        OrganizationCacheInvalidator cacheInvalidator = mock(OrganizationCacheInvalidator.class);
        EmployeeCacheInvalidator employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        EmployeeService service = new EmployeeService(
                teamService, departmentService, commonService, employeeLeaveService,
                employeeRepository, teamManagerRepository, cacheInvalidator,
                employeeCacheInvalidator, passwordEncoder);

        Employee approver = mock(Employee.class);
        Employee employee = mock(Employee.class);
        EmployeeDto.ManagedTeamsUpdateRequest request = mock(EmployeeDto.ManagedTeamsUpdateRequest.class);
        TeamCacheRow t1 = new TeamCacheRow(10L, "T1", 1L, true);
        TeamCacheRow t2 = new TeamCacheRow(20L, "T2", 1L, true);

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(approver));
        when(approver.hasPersonnelAuthority()).thenReturn(true);
        when(approver.getTeamId()).thenReturn(30L);
        when(employeeRepository.findByEmployeeNumber("E001")).thenReturn(Optional.of(employee));
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));
        when(employee.getEmployeeId()).thenReturn(1L);
        when(request.getExpectedManagedTeams()).thenReturn(List.of("T1"));
        when(request.getManagedTeams()).thenReturn(List.of("T1", "T2"));
        when(teamService.findTeamInfo("T1")).thenReturn(Optional.of(t1));
        when(teamService.findTeamInfo("T2")).thenReturn(Optional.of(t2));
        when(teamService.findTeamInfo(10L)).thenReturn(Optional.of(t1));
        when(teamService.resolveParentTeamId("T1")).thenReturn(Optional.of(30L));
        when(teamService.resolveParentTeamId("T2")).thenReturn(Optional.of(30L));
        when(teamManagerRepository.findTeamIdsByProjectManagerId(1L))
                .thenReturn(List.of(10L), List.of(10L));

        service.updateManagedTeamsByAdmin(100L, "E001", request);

        verify(teamService, never()).removeManager(any(), any());
        verify(teamService).addManager("T2", 1L, 30L);
    }


    @Test
    void employeeAdminUpdate_teamMoveWithManagerAdd_refreshesApproverFromLogicalDelta() {
        TeamService teamService = mock(TeamService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        CommonService commonService = mock(CommonService.class);
        EmployeeLeaveService employeeLeaveService = mock(EmployeeLeaveService.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        TeamManagerRepository teamManagerRepository = mock(TeamManagerRepository.class);
        OrganizationCacheInvalidator cacheInvalidator = mock(OrganizationCacheInvalidator.class);
        EmployeeCacheInvalidator employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        EmployeeService service = new EmployeeService(
                teamService, departmentService, commonService, employeeLeaveService,
                employeeRepository, teamManagerRepository, cacheInvalidator,
                employeeCacheInvalidator, passwordEncoder);

        LocalDate hireDate = LocalDate.of(2024, 1, 1);
        Employee approver = mock(Employee.class);
        Employee employee = mock(Employee.class);
        Team oldTeam = mock(Team.class);
        Team targetTeam = mock(Team.class);
        Department department = mock(Department.class);
        EmployeeDto.EmployeeAdminUpdateRequest request = mock(EmployeeDto.EmployeeAdminUpdateRequest.class);
        TeamCacheRow targetInfo = new TeamCacheRow(20L, "T2", 1L, true);

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(approver));
        when(approver.hasPersonnelAuthority()).thenReturn(true);
        when(approver.getTeamId()).thenReturn(30L);

        when(employeeRepository.findByEmployeeNumber("E001")).thenReturn(Optional.of(employee));
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));
        when(employee.getEmployeeId()).thenReturn(1L);
        when(employee.getName()).thenReturn("직원");
        when(employee.getEmail()).thenReturn("employee@example.com");
        when(employee.getPosition()).thenReturn("사원");
        when(employee.getHireDate()).thenReturn(hireDate);
        when(employee.getFireDate()).thenReturn(null);
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getTeam()).thenReturn(oldTeam);
        when(oldTeam.getTeamId()).thenReturn(10L);

        when(request.getDepartment()).thenReturn("SI사업팀");
        when(request.getManagedTeams()).thenReturn(List.of("T2"));
        when(request.getTargetTeamsForRoleSwap()).thenReturn(null);
        when(request.getTeam()).thenReturn("T2");
        when(request.getPosition()).thenReturn("사원");
        when(request.getHireDate()).thenReturn(hireDate);
        when(request.getFireDate()).thenReturn(null);

        when(departmentService.findByDepartmentName("SI사업팀")).thenReturn(Optional.of(department));
        when(teamService.findTeamInfo("T2")).thenReturn(Optional.of(targetInfo));
        when(teamService.findTeamInfo(20L)).thenReturn(Optional.of(targetInfo));
        when(teamService.resolveParentTeamId("T2")).thenReturn(Optional.of(30L));
        when(teamService.findByTeamName("T2")).thenReturn(Optional.of(targetTeam));
        when(targetTeam.getTeamId()).thenReturn(20L);
        when(targetTeam.getDepartment()).thenReturn(department);
        when(department.getDepartmentId()).thenReturn(1L);
        when(department.getDepartmentName()).thenReturn("SI사업팀");

        when(teamManagerRepository.findTeamIdsByProjectManagerId(1L))
                .thenReturn(List.of(), List.of(), List.of(20L));

        service.updateEmployeeByAdmin(100L, "E001", request);

        verify(teamService).addManager("T2", 1L, 30L);
        verify(teamService).refreshApproverIds(employee, Set.of(), Map.of(20L, 30L));
        verify(cacheInvalidator, never()).afterEmployeeOrganizationChange(any());
        verify(employeeCacheInvalidator).afterEmployeeViewChange(1L);
    }

    @Test
    void employeeAdminUpdate_replayedDesiredState_isIdempotentNoOp() {
        TeamService teamService = mock(TeamService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        CommonService commonService = mock(CommonService.class);
        EmployeeLeaveService employeeLeaveService = mock(EmployeeLeaveService.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        TeamManagerRepository teamManagerRepository = mock(TeamManagerRepository.class);
        OrganizationCacheInvalidator cacheInvalidator = mock(OrganizationCacheInvalidator.class);
        EmployeeCacheInvalidator employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        EmployeeService service = new EmployeeService(
                teamService, departmentService, commonService, employeeLeaveService,
                employeeRepository, teamManagerRepository, cacheInvalidator,
                employeeCacheInvalidator, passwordEncoder);

        Employee approver = mock(Employee.class);
        Employee initialEmployee = mock(Employee.class);
        Employee lockedEmployee = mock(Employee.class);
        Department department = mock(Department.class);
        EmployeeDto.EmployeeAdminUpdateRequest request = mock(EmployeeDto.EmployeeAdminUpdateRequest.class);
        EmployeeDto.EmployeeAdminExpectedState expected = mock(EmployeeDto.EmployeeAdminExpectedState.class);
        TeamCacheRow teamInfo = new TeamCacheRow(10L, "T1", 1L, true);
        LocalDate hireDate = LocalDate.of(2024, 1, 1);

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(approver));
        when(approver.hasPersonnelAuthority()).thenReturn(true);
        when(employeeRepository.findByEmployeeNumber("E001")).thenReturn(Optional.of(initialEmployee));
        when(initialEmployee.getEmployeeId()).thenReturn(1L);
        when(initialEmployee.getName()).thenReturn("과거이름");
        when(initialEmployee.getTeamId()).thenReturn(10L);
        when(teamManagerRepository.findTeamIdsByProjectManagerId(1L)).thenReturn(List.of());

        when(request.getDepartment()).thenReturn("개발부");
        when(request.getTeam()).thenReturn("T1");
        when(request.getName()).thenReturn("현재이름");
        when(request.getEmail()).thenReturn("current@example.com");
        when(request.getPosition()).thenReturn("사원");
        when(request.getHireDate()).thenReturn(hireDate);
        when(request.getFireDate()).thenReturn(null);
        when(request.getExpected()).thenReturn(expected);
        when(request.getManagedTeams()).thenReturn(null);
        when(request.getTargetTeamsForRoleSwap()).thenReturn(null);

        when(departmentService.findByDepartmentName("개발부")).thenReturn(Optional.of(department));
        when(teamService.findTeamInfo("T1")).thenReturn(Optional.of(teamInfo));
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lockedEmployee));

        when(lockedEmployee.getName()).thenReturn("현재이름");
        when(lockedEmployee.getEmail()).thenReturn("current@example.com");
        when(lockedEmployee.getDepartmentName()).thenReturn("개발부");
        when(lockedEmployee.getTeamName()).thenReturn("T1");
        when(lockedEmployee.getPosition()).thenReturn("사원");
        when(lockedEmployee.getHireDate()).thenReturn(hireDate);
        when(lockedEmployee.getFireDate()).thenReturn(null);

        service.updateEmployeeByAdmin(100L, "E001", request);

        verify(lockedEmployee, never()).updateInfoByAdmin(
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(teamService, never()).removeManager(any(), any());
        verify(teamService, never()).addManager(any(), any(), any());
    }

    @Test
    void employeeAdminUpdate_staleExpectedState_isRejectedWithoutWrite() {
        TeamService teamService = mock(TeamService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        CommonService commonService = mock(CommonService.class);
        EmployeeLeaveService employeeLeaveService = mock(EmployeeLeaveService.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        TeamManagerRepository teamManagerRepository = mock(TeamManagerRepository.class);
        OrganizationCacheInvalidator cacheInvalidator = mock(OrganizationCacheInvalidator.class);
        EmployeeCacheInvalidator employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        EmployeeService service = new EmployeeService(
                teamService, departmentService, commonService, employeeLeaveService,
                employeeRepository, teamManagerRepository, cacheInvalidator,
                employeeCacheInvalidator, passwordEncoder);

        Employee approver = mock(Employee.class);
        Employee initialEmployee = mock(Employee.class);
        Employee lockedEmployee = mock(Employee.class);
        Department department = mock(Department.class);
        EmployeeDto.EmployeeAdminUpdateRequest request = mock(EmployeeDto.EmployeeAdminUpdateRequest.class);
        EmployeeDto.EmployeeAdminExpectedState expected = mock(EmployeeDto.EmployeeAdminExpectedState.class);
        TeamCacheRow teamInfo = new TeamCacheRow(10L, "T1", 1L, true);
        LocalDate hireDate = LocalDate.of(2024, 1, 1);

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(approver));
        when(approver.hasPersonnelAuthority()).thenReturn(true);
        when(employeeRepository.findByEmployeeNumber("E001")).thenReturn(Optional.of(initialEmployee));
        when(initialEmployee.getEmployeeId()).thenReturn(1L);
        when(initialEmployee.getName()).thenReturn("과거이름");
        when(initialEmployee.getTeamId()).thenReturn(10L);
        when(teamManagerRepository.findTeamIdsByProjectManagerId(1L)).thenReturn(List.of());

        when(request.getDepartment()).thenReturn("개발부");
        when(request.getTeam()).thenReturn("T1");
        when(request.getName()).thenReturn("내수정");
        when(request.getEmail()).thenReturn("mine@example.com");
        when(request.getPosition()).thenReturn("사원");
        when(request.getHireDate()).thenReturn(hireDate);
        when(request.getFireDate()).thenReturn(null);
        when(request.getExpected()).thenReturn(expected);
        when(request.getManagedTeams()).thenReturn(null);
        when(request.getTargetTeamsForRoleSwap()).thenReturn(null);

        when(expected.getName()).thenReturn("과거이름");
        when(expected.getEmail()).thenReturn("old@example.com");
        when(expected.getDepartment()).thenReturn("개발부");
        when(expected.getTeam()).thenReturn("T1");
        when(expected.getPosition()).thenReturn("사원");
        when(expected.getHireDate()).thenReturn(hireDate);
        when(expected.getFireDate()).thenReturn(null);

        when(departmentService.findByDepartmentName("개발부")).thenReturn(Optional.of(department));
        when(teamService.findTeamInfo("T1")).thenReturn(Optional.of(teamInfo));
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lockedEmployee));

        when(lockedEmployee.getName()).thenReturn("다른관리자수정");
        when(lockedEmployee.getEmail()).thenReturn("other@example.com");
        when(lockedEmployee.getDepartmentName()).thenReturn("개발부");
        when(lockedEmployee.getTeamName()).thenReturn("T1");
        when(lockedEmployee.getPosition()).thenReturn("사원");
        when(lockedEmployee.getHireDate()).thenReturn(hireDate);
        when(lockedEmployee.getFireDate()).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateEmployeeByAdmin(100L, "E001", request));

        assertEquals(409, exception.getStatusCode().value());
        verify(lockedEmployee, never()).updateInfoByAdmin(
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(teamService, never()).removeManager(any(), any());
        verify(teamService, never()).addManager(any(), any(), any());
    }

}
