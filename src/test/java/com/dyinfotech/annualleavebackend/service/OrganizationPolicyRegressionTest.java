package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeViewCacheKey;
import com.dyinfotech.annualleavebackend.common.cache.OrganizationCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.type.PositionType;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.domain.TeamManager;
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
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
        TeamCacheRow parent = new TeamCacheRow(20L, "P팀", 1L, true);
        TeamManagerCacheRow peer = manager(10L, 1L, 20L, null);
        TeamManagerCacheRow self = manager(10L, 2L, 20L, null);
        TeamManagerCacheRow parentManager = manager(20L, 3L, 20L, null);
        prepareCaches(List.of(team, parent), List.of(peer, self, parentManager));

        Employee employee = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(2L);
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getApproverId()).thenReturn(1L);

        Set<Long> approvers = teamService.resolveCurrentApproverIds(employee);

        assertEquals(Set.of(3L), approvers);
        verify(employeeRepository, never()).findAllById(any());
        verify(employeeRepository, never()).findById(any());
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
        when(departmentRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(newDepartment));
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
        assertTrue(exception.getReason().contains("결재 의존성"));
        verify(teamRepository).findByIdForUpdate(10L);
        // 삭제 후 최종 상태 검증에서 실패하고 실제 transaction은 rollback된다.
        verify(teamManagerRepository).deleteById(id);
    }

    @Test
    void retiredManager_cannotReplaceExistingTeamManager() {
        Team team = mock(Team.class);
        Employee retiredManager = mock(Employee.class);
        TeamDto.UpdateRequest request = new TeamDto.UpdateRequest();
        setField(request, "projectManagerId", 99L);

        Team ceoTeam = mock(Team.class);
        when(teamCache.get("대표이사"))
                .thenReturn(List.of(new TeamCacheRow(1L, "대표이사", 1L, true)));
        when(teamRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ceoTeam));
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
    void scheduledRetirement_thenOtherManagerRemoval_rejectsFutureGap() {
        TeamManagerId removedId = new TeamManagerId(10L, 2L);
        TeamManager remainingRow = mock(TeamManager.class);
        Employee scheduledManager = mock(Employee.class);
        Employee indefiniteEmployee = mock(Employee.class);

        when(teamRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mock(Team.class)));
        when(teamManagerRepository.existsById(removedId)).thenReturn(true);
        when(teamManagerRepository.findAllByTeam_TeamId(10L)).thenReturn(List.of(remainingRow));
        when(remainingRow.getProjectManager()).thenReturn(scheduledManager);
        when(scheduledManager.isActive(TODAY)).thenReturn(true);
        when(scheduledManager.getFireDate()).thenReturn(TODAY.plusDays(30));
        when(employeeRepository.findAllByTeam_TeamId(10L)).thenReturn(List.of(indefiniteEmployee));
        when(indefiniteEmployee.isActive(TODAY)).thenReturn(true);
        when(indefiniteEmployee.getFireDate()).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> teamService.removeManager(10L, 2L)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("미래 결재 공백"));
        verify(teamManagerRepository).deleteById(removedId);
    }

    @Test
    void scheduledRetirementManager_cannotBecomeSoleManagerForIndefiniteDependents() {
        Team team = mock(Team.class);
        Team parent = mock(Team.class);
        TeamManager oldManager = mock(TeamManager.class);
        TeamManager replacementRow = mock(TeamManager.class);
        Employee scheduledManager = mock(Employee.class);
        Employee indefiniteEmployee = mock(Employee.class);
        TeamDto.UpdateRequest request = new TeamDto.UpdateRequest();
        setField(request, "projectManagerId", 99L);
        setField(request, "parentTeamId", 20L);

        when(teamRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(team));
        when(teamRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(parent));
        when(team.getTeamId()).thenReturn(10L);
        when(team.getEnabled()).thenReturn(true);
        when(team.getTeamName()).thenReturn("플랫폼팀");
        when(parent.getTeamId()).thenReturn(20L);
        when(parent.getEnabled()).thenReturn(true);
        when(teamManagerRepository.existsActiveManagerInTeam(20L, TODAY)).thenReturn(true);
        when(teamManagerRepository.findAllByTeam_TeamId(10L))
                .thenReturn(List.of(oldManager), List.of(replacementRow));
        when(teamManagerRepository.findAllByTeam_TeamId(20L)).thenReturn(List.of());
        when(oldManager.getParentTeamId()).thenReturn(20L);
        when(employeeRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(scheduledManager));
        when(scheduledManager.isActive(TODAY)).thenReturn(true);
        when(scheduledManager.getFireDate()).thenReturn(TODAY.plusDays(30));
        when(replacementRow.getProjectManager()).thenReturn(scheduledManager);
        when(employeeRepository.findAllByTeam_TeamId(10L)).thenReturn(List.of(indefiniteEmployee));
        when(indefiniteEmployee.isActive(TODAY)).thenReturn(true);
        when(indefiniteEmployee.getFireDate()).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> teamService.updateTeam(10L, request)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("미래 결재 공백"));
    }

    @Test
    void newIndefiniteDependent_rejectsScheduledOnlyManagerCoverage() {
        TeamManager managerRow = mock(TeamManager.class);
        Employee scheduledManager = mock(Employee.class);
        Employee indefiniteEmployee = mock(Employee.class);

        when(teamRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mock(Team.class)));
        when(teamManagerRepository.findAllByTeam_TeamId(10L)).thenReturn(List.of(managerRow));
        when(managerRow.getProjectManager()).thenReturn(scheduledManager);
        when(scheduledManager.isActive(TODAY)).thenReturn(true);
        when(scheduledManager.getFireDate()).thenReturn(TODAY.plusDays(30));
        when(employeeRepository.findAllByTeam_TeamId(10L)).thenReturn(List.of(indefiniteEmployee));
        when(indefiniteEmployee.isActive(TODAY)).thenReturn(true);
        when(indefiniteEmployee.getFireDate()).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> teamService.validateFutureApprovalCoverage(Set.of(10L))
        );

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("미래 결재 공백"));
    }

    @Test
    void teamLocks_areAcquiredInAscendingOrder() {
        when(teamRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mock(Team.class)));
        when(teamRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(mock(Team.class)));

        teamService.lockTeamsForUpdate(List.of(20L, 10L, 20L));

        InOrder inOrder = inOrder(teamRepository);
        inOrder.verify(teamRepository).findByIdForUpdate(10L);
        inOrder.verify(teamRepository).findByIdForUpdate(20L);
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
    void currentApprover_usesCachedOrganizationInsteadOfStaleStoredPointer() {
        TeamCacheRow team = new TeamCacheRow(10L, "T팀", 1L, true);
        TeamManagerCacheRow currentManager = manager(10L, 3L, 20L, null);
        prepareCaches(List.of(team), List.of(currentManager));

        Employee employee = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(2L);
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getApproverId()).thenReturn(1L);

        Employee resolved = mock(Employee.class);
        when(resolved.getEmployeeId()).thenReturn(3L);
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(resolved));

        assertEquals(3L, teamService.resolveCurrentApprover(employee).getEmployeeId());
        verify(employeeRepository, never()).findAllById(any());
    }

    @Test
    void refreshApproverIds_repairsStaleStoredPointerFromOrganizationCache() {
        TeamCacheRow team = new TeamCacheRow(10L, "T팀", 1L, true);
        TeamManagerCacheRow currentManager = manager(10L, 3L, 20L, null);
        prepareCaches(List.of(team), List.of(currentManager));

        Employee employee = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(2L);
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getApproverId()).thenReturn(1L);

        Employee currentApprover = mock(Employee.class);
        when(employeeRepository.getReferenceById(3L)).thenReturn(currentApprover);

        Set<Long> approverIds = teamService.refreshApproverIds(employee);

        assertEquals(Set.of(3L), approverIds);
        verify(employee).changeApprover(currentApprover);
    }

    @Test
    void refreshApproverIds_keepsStoredPointerWhenStillCurrentCandidate() {
        TeamCacheRow team = new TeamCacheRow(10L, "ABC", 1L, true);
        prepareCaches(
                List.of(team),
                List.of(manager(10L, 31L, 20L, null), manager(10L, 42L, 20L, null))
        );

        Employee employee = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(2L);
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getApproverId()).thenReturn(42L);

        teamService.refreshApproverIds(employee);

        verify(employee, never()).changeApprover(any());
        verify(employeeRepository, never()).getReferenceById(any());
        verifyNoInteractions(teamManagerRepository);
    }

    @Test
    void refreshApproverIds_selectsLowestCandidateDeterministically() {
        TeamCacheRow team = new TeamCacheRow(10L, "ABC", 1L, true);
        prepareCaches(
                List.of(team),
                List.of(manager(10L, 42L, 20L, null), manager(10L, 31L, 20L, null))
        );

        Employee employee = mock(Employee.class);
        Employee manager31 = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(2L);
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getApproverId()).thenReturn(17L);
        when(employeeRepository.getReferenceById(31L)).thenReturn(manager31);

        teamService.refreshApproverIds(employee);

        verify(employee).changeApprover(manager31);
    }

    @Test
    void refreshApproverIds_appliesSameTransactionManagerAddDelta() {
        TeamCacheRow team = new TeamCacheRow(10L, "ABC", 1L, true);
        TeamCacheRow parent = new TeamCacheRow(20L, "PARENT", 1L, true);
        prepareCaches(
                List.of(team, parent),
                List.of(manager(10L, 31L, 20L, null), manager(20L, 50L, 20L, null))
        );

        Employee employee = mock(Employee.class);
        Employee parentManager = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(2L);
        when(employee.getEmployeeNumber()).thenReturn("E002");
        when(employee.getName()).thenReturn("직원");
        when(employee.getPosition()).thenReturn("부장");
        when(employee.getHireDate()).thenReturn(TODAY.minusYears(1));
        when(employee.getFireDate()).thenReturn(null);
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getApproverId()).thenReturn(31L);
        when(employeeRepository.getReferenceById(50L)).thenReturn(parentManager);

        teamService.refreshApproverIds(employee, Set.of(), Map.of(10L, 20L));

        verify(employee).changeApprover(parentManager);
        verifyNoInteractions(teamManagerRepository);
    }

    @Test
    void refreshApproverIds_appliesSameTransactionManagerRemoveDelta() {
        TeamCacheRow team = new TeamCacheRow(10L, "ABC", 1L, true);
        TeamCacheRow parent = new TeamCacheRow(20L, "PARENT", 1L, true);
        prepareCaches(
                List.of(team, parent),
                List.of(
                        manager(10L, 2L, 20L, null),
                        manager(10L, 31L, 20L, null),
                        manager(20L, 50L, 20L, null)
                )
        );

        Employee employee = mock(Employee.class);
        Employee peerManager = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(2L);
        when(employee.getTeamId()).thenReturn(10L);
        when(employee.getApproverId()).thenReturn(50L);
        when(employeeRepository.getReferenceById(31L)).thenReturn(peerManager);

        teamService.refreshApproverIds(employee, Set.of(10L), Map.of());

        verify(employee).changeApprover(peerManager);
        verifyNoInteractions(teamManagerRepository);
    }

    @Test
    void representativeDirectorAlias_mapsToCeo() {
        assertEquals(PositionType.CEO, PositionType.getType("대표이사"));
        assertEquals(PositionType.CEO, PositionType.getType("사장"));
    }

    @Test
    void employeeViewCacheGeneration_separatesLateStalePutFromFreshLookup() {
        EmployeeViewCacheKey cacheKey = new EmployeeViewCacheKey();
        Cache<String, String> cache = Caffeine.newBuilder().build();

        String staleInFlightKey = cacheKey.key(1L);
        cacheKey.bumpOrganization();
        String freshKey = cacheKey.key(1L);

        // 조직 변경 전에 시작한 조회가 무효화 이후 늦게 완료되어도 구세대 키에만 저장된다.
        cache.put(staleInFlightKey, "stale");

        assertNotEquals(staleInFlightKey, freshKey);
        assertNull(cache.getIfPresent(freshKey));

        cacheKey.bumpEmployee(1L);
        assertNotEquals(freshKey, cacheKey.key(1L));
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
                TODAY.minusYears(1),
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
    @Test
    void addManager_sameRelationship_isIdempotentNoOp() {
        TeamCacheRow teamInfo = new TeamCacheRow(10L, "플랫폼팀", 1L, true);
        Team team = mock(Team.class);
        Employee manager = mock(Employee.class);
        TeamManager existing = mock(TeamManager.class);

        when(teamCache.get("플랫폼팀")).thenReturn(List.of(teamInfo));
        when(teamRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(team));
        when(team.getTeamId()).thenReturn(10L);
        when(team.getEnabled()).thenReturn(true);
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(manager));
        when(manager.isActive(TODAY)).thenReturn(true);
        when(existing.getProjectManagerId()).thenReturn(1L);
        when(existing.getParentTeamId()).thenReturn(10L);
        when(teamManagerRepository.findAllByTeam_TeamId(10L)).thenReturn(List.of(existing));

        teamService.addManager("플랫폼팀", 1L, 10L);

        verify(teamManagerRepository, never()).save(any(TeamManager.class));
        verify(cacheInvalidator, never()).afterTeamManagerChange(any());
    }

    @Test
    void updateTeam_sameManagerAndParent_isIdempotentNoOp() {
        Team team = mock(Team.class);
        Employee manager = mock(Employee.class);
        TeamManager existing = mock(TeamManager.class);
        TeamDto.UpdateRequest request = new TeamDto.UpdateRequest();
        setField(request, "projectManagerId", 1L);

        when(teamManagerRepository.findAllByTeam_TeamId(10L)).thenReturn(List.of(existing));
        when(existing.getParentTeamId()).thenReturn(10L);
        when(existing.getProjectManagerId()).thenReturn(1L);
        when(teamRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(team));
        when(team.getTeamId()).thenReturn(10L);
        when(team.getEnabled()).thenReturn(true);
        when(team.getTeamName()).thenReturn("플랫폼팀");
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(manager));
        when(manager.isActive(TODAY)).thenReturn(true);

        teamService.updateTeam(10L, request);

        verify(teamManagerRepository, never()).deleteAll(any());
        verify(teamManagerRepository, never()).save(any(TeamManager.class));
        verify(cacheInvalidator, never()).afterTeamManagerChange(any());
    }

    @Test
    void createTeam_sameIdempotencyKeyAndPayload_replaysOriginalTeamId() {
        TeamDto.CreateRequest request = new TeamDto.CreateRequest();
        setField(request, "teamName", "플랫폼팀");
        setField(request, "departmentId", 1L);

        Department department = mock(Department.class);
        when(department.getEnabled()).thenReturn(true);
        when(departmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(department));
        when(teamRepository.findByTeamName("플랫폼팀")).thenReturn(Optional.empty());

        AtomicReference<Team> created = new AtomicReference<>();
        when(teamRepository.findByCreateRequestKey("request-key-0001"))
                .thenAnswer(invocation -> Optional.ofNullable(created.get()));
        when(teamRepository.saveAndFlush(any(Team.class))).thenAnswer(invocation -> {
            Team team = invocation.getArgument(0);
            setField(team, "teamId", 99L);
            created.set(team);
            return team;
        });

        Long first = teamService.createTeam(100L, request, "request-key-0001");
        Long replay = teamService.createTeam(100L, request, "request-key-0001");

        assertEquals(99L, first);
        assertEquals(99L, replay);
        verify(teamRepository).saveAndFlush(any(Team.class));
    }

    @Test
    void createTeam_sameIdempotencyKeyDifferentPayload_isRejected() {
        TeamDto.CreateRequest firstRequest = new TeamDto.CreateRequest();
        setField(firstRequest, "teamName", "플랫폼팀");
        setField(firstRequest, "departmentId", 1L);

        TeamDto.CreateRequest secondRequest = new TeamDto.CreateRequest();
        setField(secondRequest, "teamName", "운영팀");
        setField(secondRequest, "departmentId", 1L);

        Department department = mock(Department.class);
        when(department.getEnabled()).thenReturn(true);
        when(departmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(department));
        when(teamRepository.findByTeamName("플랫폼팀")).thenReturn(Optional.empty());

        AtomicReference<Team> created = new AtomicReference<>();
        when(teamRepository.findByCreateRequestKey("request-key-0002"))
                .thenAnswer(invocation -> Optional.ofNullable(created.get()));
        when(teamRepository.saveAndFlush(any(Team.class))).thenAnswer(invocation -> {
            Team team = invocation.getArgument(0);
            setField(team, "teamId", 100L);
            created.set(team);
            return team;
        });

        teamService.createTeam(100L, firstRequest, "request-key-0002");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> teamService.createTeam(100L, secondRequest, "request-key-0002"));

        assertEquals(409, exception.getStatusCode().value());
        verify(teamRepository).saveAndFlush(any(Team.class));
    }

}
