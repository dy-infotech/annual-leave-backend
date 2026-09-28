package com.dyinfotech.annualleavebackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.domain.TeamManager.TeamManagerId;
import com.dyinfotech.annualleavebackend.repository.query.TeamManagerRepositoryCustom;

public interface TeamManagerRepository
        extends JpaRepository<TeamManager, TeamManagerId>, TeamManagerRepositoryCustom {

    boolean existsByProjectManager_EmployeeId(Long projectManagerId);

    List<TeamManager> findAllByTeam_TeamId(Long teamId);

    boolean existsByParentTeam_TeamIdAndTeam_TeamIdNot(Long parentTeamId, Long teamId);

    @Modifying
    long deleteByTeam_TeamId(Long teamId);
}
