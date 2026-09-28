package com.dyinfotech.annualleavebackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByEnabledTrue();

    Optional<Team> findByTeamName(String teamName);

    Optional<Team> findByTeamNameAndEnabledTrue(String teamName);

    boolean existsByDepartment_DepartmentIdAndEnabledTrue(Long departmentId);

    @Query("select new com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow("
            + "t.teamId, t.teamName, t.department.departmentId, t.enabled) "
            + "from Team t where t.enabled = true")
    List<TeamCacheRow> findAllEnabledForCache();

    @Query("select new com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow("
            + "t.teamId, t.teamName, t.department.departmentId, t.enabled) "
            + "from Team t where t.teamName = :teamName and t.enabled = true")
    Optional<TeamCacheRow> findByNameEnabledForCache(@Param("teamName") String teamName);
}
