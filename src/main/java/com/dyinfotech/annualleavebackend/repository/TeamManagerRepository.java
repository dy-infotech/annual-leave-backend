package com.dyinfotech.annualleavebackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.domain.TeamManager.TeamManagerId;
import com.dyinfotech.annualleavebackend.repository.projection.TeamManagerCacheRow;

public interface TeamManagerRepository extends JpaRepository<TeamManager, TeamManagerId> {

    boolean existsByProjectManager_EmployeeId(Long projectManagerId);

    List<TeamManager> findAllByTeam_TeamId(Long teamId);

    boolean existsByParentTeam_TeamIdAndTeam_TeamIdNot(Long parentTeamId, Long teamId);

    @Modifying
    long deleteByTeam_TeamId(Long teamId);

    @Query("select new com.dyinfotech.annualleavebackend.repository.projection.TeamManagerCacheRow("
            + "tm.team.teamId, pm.employeeId, tm.parentTeam.teamId, "
            + "pm.employeeNumber, pm.name, pm.position, pm.fireDate) "
            + "from TeamManager tm join tm.projectManager pm")
    List<TeamManagerCacheRow> findAllForCache();

    @Query("select new com.dyinfotech.annualleavebackend.repository.projection.TeamManagerCacheRow("
            + "tm.team.teamId, pm.employeeId, tm.parentTeam.teamId, "
            + "pm.employeeNumber, pm.name, pm.position, pm.fireDate) "
            + "from TeamManager tm join tm.projectManager pm "
            + "where tm.team.teamId = :teamId")
    List<TeamManagerCacheRow> findAllByTeamIdForCache(@Param("teamId") Long teamId);
}
