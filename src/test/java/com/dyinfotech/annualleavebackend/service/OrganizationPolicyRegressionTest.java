package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeViewCacheKey;
import com.dyinfotech.annualleavebackend.common.cache.OrganizationCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.type.PositionType;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.domain.TeamManager.TeamManagerId;
import com.dyinfotech.annualleavebackend.dto.TeamDto;
import com.dyinfotech.annualleavebackend.repository.DepartmentRepository;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.TeamManagerRepository;
import com.dyinfotech.annualleavebackend.repository.TeamRepository;
import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamManagerCacheRow;
import com.dyinfotech.annualleavebackend.service.TeamService.ManagedTeam;
import com.github.benmanes.caffeine.cache.LoadingCache;

class OrganizationPolicyRegressionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    private LoadingCache<String, List<TeamCacheRow>> teamCache;
    private LoadingCache<String, List<TeamManagerCacheRow>> managerCache;
    private LoadingCache<String, List<DepartmentCacheRow>> departmentCache;
    private TeamRepository teamRepository;
    private TeamManagerRepository teamManagerRepository;
    private EmployeeRepository employeeRepository;
    private DepartmentRepository departmentRepository;
    private OrganizationCacheInvalidator cacheInvalidator;
    private TeamService teamService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        teamCache = mock(LoadingCache.class);
        managerCache = mock(LoadingCache.class);
        departmentCache = mock(LoadingCache.class);
        teamRepository = mock(TeamRepository.class);
        teamManagerRepository = mock(TeamManagerRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        cacheInvalidator = mock(OrganizationCacheInvalidator.class);

        Clock clock = Clock.fixed(
                Instant.parse("2026-09-28T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        teamService = new TeamService(
                teamCache,
                managerCache,
                departmentCache,
                teamRepository,
                teamManagerRepository,
                employeeRepository,
                departmentRepository,
                cacheInvalidator,
                clock
        );
    }

    @Test
    void pmApprover_usesOnlyParentManagers_notPeerManagers() {
        TeamCacheRow team = new TeamCacheRow(10L, "T팀", 1L, true);
        TeamCacheRow parent = new TeamCacheRow(20L, "상위팀", 1L, true);
        TeamManagerCacheRow peer = manager(10L, 1L, 20L, null);
        TeamManagerCacheRow self = manager(10L, 2L, 20L, null);
        TeamManagerCacheRow parentManager = manager(20L, 3L, 20L, null);
        prepareCaches(List.of(team, parent), List.of(peer, self, parentManager));

        Employee employee = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(2L);
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getApproverId()).thenReturn(1L);

        Employee resolvedParent = mock(Employee.class);
        when(resolvedParent.getEmployeeId()).thenReturn(3L);
        when(resolvedParent.isActive(TODAY)).thenReturn(true);
        when(employeeRepository.findAllById(argThat(ids -> {
            Set<Long> actual = new HashSet<>();
            ids.forEach(actual::add);
            return actual.equals(Set.of(3L));
        }))).thenReturn(List.of(resolvedParent));

        Set<Long> approvers = teamService.refreshApproverIds(employee);

        assertEquals(Set.of(3L), approvers);
    }

    @Test
    void hierarchyTraversal_keepsDescendantsWhenMiddleManagerRetired() {
        TeamCacheRow a = new TeamCacheRow(10L, "A팀", 1L, true);
        TeamCacheRow b = new TeamCacheRow(20L, "B팀", 1L, true);
        TeamCacheRow c = new TeamCacheRow(30L, "C팀", 1L, true);

        TeamManagerCacheRow aManager = manager(10L, 1L, 10L, null);
        TeamManagerCacheRow retiredBManager = manager(
                20L, 2L, 10L, TODAY.minusDays(1));
        TeamManagerCacheRow cManager = manager(30L, 3L, 20L, null);

        prepareCaches(
                List.of(a, b, c),
                List.of(aManager, retiredBManager, cManager)
        );

        Set<String> teamNames = teamService.getSelfAndDescendants("A팀").stream()
                .map(ManagedTeam::teamName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("A팀", "B팀", "C팀"), teamNames);
    }

    @Test
    void departmentOnlyTeamUpdate_doesNotFailOnDuplicateInvalidationKey() {
        Team team = mock(Team.class);
        Department oldDepartment = mock(Department.class);
        Department newDepartment = mock(Department.class);
        TeamDto.UpdateRequest request = new TeamDto.UpdateRequest();
        setField(request, "departmentId", 2L);

        when(teamRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(team));
        when(team.getEnabled()).thenReturn(true);
        when(team.getTeamName()).thenReturn("플랫폼팀");
        when(team.getDepartment()).thenReturn(oldDepartment);
        when(oldDepartment.getDepartmentId()).thenReturn(1L);
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(newDepartment));
        when(newDepartment.getDepartmentId()).thenReturn(2L);
        when(newDepartment.getEnabled()).thenReturn(true);
        when(employeeRepository.findAllByTeam_TeamId(10L)).thenReturn(List.of());
        when(teamManagerRepository.findAllByTeam_TeamId(10L)).thenReturn(List.of());

        teamService.updateTeam(10L, request);

        verify(cacheInvalidator).afterTeamChange(
                argThat(names -> names.size() == 1 && names.contains("플랫폼팀")),
                eq(true)
        );
    }

    @Test
    void managerlessTeam_rejectsEmployeeAssignment() {
        when(teamRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mock(Team.class)));
        when(teamManagerRepository.existsActiveManagerInTeam(10L, TODAY)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> teamService.requireActiveManager(10L)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("담당자가 없는 팀"));
        verify(teamRepository).findByIdForUpdate(10L);
    }

    @Test
    void lastActiveManager_cannotBeRemovedWhenChildTeamDependsOnIt() {
        TeamManagerId id = new TeamManagerId(10L, 1L);
        when(teamRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mock(Team.class)));
        when(teamManagerRepository.existsById(id)).thenReturn(true);
        when(teamManagerRepository.existsOtherActiveManagerInTeam(10L, 1L, TODAY)).thenReturn(false);
        when(employeeRepository.existsActiveEmployeeInTeam(10L, TODAY)).thenReturn(false);
        when(teamManagerRepository.existsByParentTeam_TeamIdAndTeam_TeamIdNot(10L, 10L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> teamService.removeManager(10L, 1L)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("마지막 재직 담당자"));
        verify(teamRepository).findByIdForUpdate(10L);
        verify(teamManagerRepository, never()).deleteById(id);
    }

    @Test
    void retiredManager_cannotReplaceExistingTeamManager() {
        Team team = mock(Team.class);
        Employee retiredManager = mock(Employee.class);
        TeamDto.UpdateRequest request = new TeamDto.UpdateRequest();
        setField(request, "projectManagerId", 99L);

        when(teamRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(team));
        when(team.getEnabled()).thenReturn(true);
        when(team.getTeamName()).thenReturn("플랫폼팀");
        when(teamManagerRepository.findAllByTeam_TeamId(10L)).thenReturn(List.of());
        when(employeeRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(retiredManager));
        when(retiredManager.isActive(TODAY)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> teamService.updateTeam(10L, request)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("퇴사 처리된 사원"));
        verify(teamManagerRepository, never()).deleteAll(any());
    }

    @Test
    void scheduledRetirement_rejectsFutureApprovalGap() {
        LocalDate fireDate = TODAY.plusDays(10);
        LocalDate inactiveFrom = fireDate.plusDays(1);

        when(teamManagerRepository.findTeamIdsByProjectManagerId(1L)).thenReturn(List.of(10L));
        when(teamRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mock(Team.class)));
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(Employee.class)));
        when(teamManagerRepository.existsOtherActiveManagerInTeam(10L, 1L, inactiveFrom)).thenReturn(false);
        when(employeeRepository.existsActiveEmployeeInTeamExcludingEmployee(10L, 1L, inactiveFrom)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> teamService.validateManagerDeactivation(1L, fireDate)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("예약 퇴사일 이후 결재 공백"));
    }

    @Test
    void currentApprover_usesLiveOrganizationInsteadOfStaleStoredPointer() {
        TeamCacheRow team = new TeamCacheRow(10L, "T팀", 1L, true);
        TeamManagerCacheRow currentManager = manager(10L, 3L, 20L, null);
        prepareCaches(List.of(team), List.of(currentManager));

        Employee employee = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(2L);
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getApproverId()).thenReturn(1L);

        Employee resolved = mock(Employee.class);
        when(resolved.getEmployeeId()).thenReturn(3L);
        when(resolved.isActive(TODAY)).thenReturn(true);
        when(employeeRepository.findAllById(any())).thenReturn(List.of(resolved));

        assertEquals(3L, teamService.resolveCurrentApprover(employee).getEmployeeId());
    }
    @Test
    void representativeDirectorAlias_mapsToCeo() {
        assertEquals(PositionType.CEO, PositionType.getType("대표이사"));
        assertEquals(PositionType.CEO, PositionType.getType("사장"));
    }

    @Test
    void employeeViewCacheGeneration_separatesStaleInFlightKeys() {
        EmployeeViewCacheKey cacheKey = new EmployeeViewCacheKey();

        String initial = cacheKey.key(1L);
        cacheKey.bumpOrganization();
        String afterOrganizationChange = cacheKey.key(1L);
        cacheKey.bumpEmployee(1L);
        String afterEmployeeChange = cacheKey.key(1L);

        assertNotEquals(initial, afterOrganizationChange);
        assertNotEquals(afterOrganizationChange, afterEmployeeChange);
    }

    private void prepareCaches(
            List<TeamCacheRow> teams,
            List<TeamManagerCacheRow> managers) {
        when(teamCache.get(CacheConfig.TOTAL_KEY)).thenReturn(teams);
        for (TeamCacheRow team : teams) {
            when(teamCache.get(team.teamName())).thenReturn(List.of(team));
            when(managerCache.get(String.valueOf(team.teamId())))
                    .thenReturn(managers.stream()
                            .filter(manager -> manager.teamId().equals(team.teamId()))
                            .toList());
        }
        when(managerCache.get(CacheConfig.TOTAL_KEY)).thenReturn(managers);
        when(departmentCache.get(CacheConfig.TOTAL_KEY)).thenReturn(List.of());
    }

    private static TeamManagerCacheRow manager(
            Long teamId,
            Long employeeId,
            Long parentTeamId,
            LocalDate fireDate) {
        return new TeamManagerCacheRow(
                teamId,
                employeeId,
                parentTeamId,
                "E" + employeeId,
                "관리자" + employeeId,
                "부장",
                fireDate
        );
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
