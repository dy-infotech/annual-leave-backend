package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDate;
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

    @Query("select tm.team.teamId from TeamManager tm where tm.projectManager.employeeId = :projectManagerId")
    List<Long> findTeamIdsByProjectManagerId(@Param("projectManagerId") Long projectManagerId);

    List<TeamManager> findAllByTeam_TeamId(Long teamId);

    @Query("select count(tm) > 0 from TeamManager tm "
            + "where tm.team.teamId = :teamId "
            + "and (tm.projectManager.fireDate is null or tm.projectManager.fireDate >= :today)")
    boolean existsActiveManagerInTeam(@Param("teamId") Long teamId, @Param("today") LocalDate today);

    @Query("select count(tm) > 0 from TeamManager tm "
            + "where tm.team.teamId = :teamId "
            + "and tm.projectManager.employeeId <> :employeeId "
            + "and (tm.projectManager.fireDate is null or tm.projectManager.fireDate >= :today)")
    boolean existsOtherActiveManagerInTeam(
            @Param("teamId") Long teamId,
            @Param("employeeId") Long employeeId,
            @Param("today") LocalDate today);

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
