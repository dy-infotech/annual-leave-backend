package com.dyinfotech.annualleavebackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.domain.TeamManager.TeamManagerId;

public interface TeamManagerRepository extends JpaRepository<TeamManager, TeamManagerId> {
	boolean existsByProjectManager_EmployeeId(Long projectManagerId);
	TeamManager findFirstByTeam_TeamNameOrderByProjectManager_EmployeeIdAsc(String team);
    List<TeamManager> findAllByTeam_TeamNameOrderByTeam_TeamIdAsc(String team);
}