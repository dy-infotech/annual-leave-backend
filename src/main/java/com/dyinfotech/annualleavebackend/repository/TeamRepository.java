package com.dyinfotech.annualleavebackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
	@Override
	@EntityGraph(attributePaths = "projectManager")
	List<Team> findAll();
	boolean existsByProjectManager_EmployeeId(Long projectManagerId);
	Team findFirstByTeamOrderBySeqAsc(String team);
    @EntityGraph(attributePaths = "projectManager")
    List<Team> findAllByTeamOrderBySeqAsc(String team);
}