package com.dyinfotech.annualleavebackend.common.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dyinfotech.annualleavebackend.common.transaction.AfterCommitExecutor;
import com.dyinfotech.annualleavebackend.common.type.ManageType;
import com.dyinfotech.annualleavebackend.common.type.PositionType;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.DepartmentRepository;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.TeamManagerRepository;
import com.dyinfotech.annualleavebackend.repository.TeamRepository;
import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamManagerCacheRow;
import com.dyinfotech.annualleavebackend.service.TeamService;
import com.dyinfotech.annualleavebackend.dto.TeamDto;
import com.github.benmanes.caffeine.cache.LoadingCache;

class OrganizationCacheRegressionTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void teamInvalidation_runsOnlyAfterCommit() {
        @SuppressWarnings("unchecked")
        LoadingCache<String, List<DepartmentCacheRow>> departmentCache = mock(LoadingCache.class);
        @SuppressWarnings("unchecked")
        LoadingCache<String, List<TeamCacheRow>> teamCache = mock(LoadingCache.class);
        @SuppressWarnings("unchecked")
        LoadingCache<String, List<TeamManagerCacheRow>> teamManagerCache = mock(LoadingCache.class);

        CacheManager cacheManager = mock(CacheManager.class);
        Cache employeeCache = mock(Cache.class);
        Cache managementCache = mock(Cache.class);
        when(cacheManager.getCache(CacheConfig.CACHE_EMPLOYEES)).thenReturn(employeeCache);
        when(cacheManager.getCache(CacheConfig.CACHE_TEAM_MANAGEMENT_DATA)).thenReturn(managementCache);

        OrganizationCacheInvalidator invalidator = new OrganizationCacheInvalidator(
                departmentCache,
                teamCache,
                teamManagerCache,
                cacheManager,
                new AfterCommitExecutor()
        );

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        invalidator.afterTeamChange(Set.of("기존팀", "변경팀"), true);

        verify(teamCache, never()).invalidate(CacheConfig.TOTAL_KEY);
        verify(teamCache, never()).invalidate("기존팀");
        verify(teamCache, never()).invalidate("변경팀");
        verify(employeeCache, never()).clear();
        verify(managementCache, never()).clear();

        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(teamCache).invalidate(CacheConfig.TOTAL_KEY);
        verify(teamCache).invalidate("기존팀");
        verify(teamCache).invalidate("변경팀");
        verify(employeeCache).clear();
        verify(managementCache).clear();
    }

    @Test
    void existingTeamWithoutManager_isNotTreatedAsNewTeam() {
        TeamCacheRow team = new TeamCacheRow(10L, "플랫폼팀", 1L, true);
        TeamService teamService = createTeamService(
                List.of(team),
                List.of(new DepartmentCacheRow(1L, "SI사업팀", true)),
                List.of()
        );

        Employee approver = mock(Employee.class);
        when(approver.getEmployeeId()).thenReturn(1L);
        when(approver.getPosition()).thenReturn(PositionType.CEO.getName());

        var teamData = teamService.getTeamManagerData("플랫폼팀", approver);

        assertFalse(ManageType.IS_NEW_TEAM.contains(teamData.getKey()));
        assertTrue(ManageType.IS_TEAM_MANAGER.contains(teamData.getKey()));
    }

    @Test
    void managerlessTeam_isReturnedByAdminTeamQuery() {
        TeamCacheRow team = new TeamCacheRow(10L, "플랫폼팀", 1L, true);
        TeamService teamService = createTeamService(
                List.of(team),
                List.of(new DepartmentCacheRow(1L, "SI사업팀", true)),
                List.of()
        );

        List<TeamDto.TeamResponse> responses = teamService.findAllForAdmin();

        assertEquals(1, responses.size());
        assertEquals("플랫폼팀", responses.get(0).getTeamName());
        assertEquals("SI사업팀", responses.get(0).getDepartmentName());
        assertTrue(responses.get(0).getManagers().isEmpty());
        assertEquals(null, responses.get(0).getParentTeamId());
    }

    private TeamService createTeamService(
            List<TeamCacheRow> teams,
            List<DepartmentCacheRow> departments,
            List<TeamManagerCacheRow> managers) {
        @SuppressWarnings("unchecked")
        LoadingCache<String, List<TeamCacheRow>> teamCache = mock(LoadingCache.class);
        @SuppressWarnings("unchecked")
        LoadingCache<String, List<TeamManagerCacheRow>> managerCache = mock(LoadingCache.class);
        @SuppressWarnings("unchecked")
        LoadingCache<String, List<DepartmentCacheRow>> departmentCache = mock(LoadingCache.class);

        when(teamCache.get(CacheConfig.TOTAL_KEY)).thenReturn(teams);
        for (TeamCacheRow team : teams) {
            when(teamCache.get(team.teamName())).thenReturn(List.of(team));
            when(managerCache.get(String.valueOf(team.teamId())))
                    .thenReturn(managers.stream()
                            .filter(manager -> manager.teamId().equals(team.teamId()))
                            .toList());
        }
        when(managerCache.get(CacheConfig.TOTAL_KEY)).thenReturn(managers);
        when(departmentCache.get(CacheConfig.TOTAL_KEY)).thenReturn(departments);

        TeamRepository teamRepository = mock(TeamRepository.class);
        TeamManagerRepository teamManagerRepository = mock(TeamManagerRepository.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        OrganizationCacheInvalidator cacheInvalidator = mock(OrganizationCacheInvalidator.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-28T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        return new TeamService(
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
}
