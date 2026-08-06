package com.dyinfotech.annualleavebackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.repository.query.TeamRepositoryCustom;

public interface TeamRepository extends JpaRepository<Team, Long>, TeamRepositoryCustom {
	boolean existsByProjectManager_EmployeeId(Long projectManagerId);
	Team findFirstByTeamOrderBySeqAsc(String team);
    List<Team> findAllByTeamOrderBySeqAsc(String team);
}