package com.dyinfotech.annualleavebackend.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

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
}
