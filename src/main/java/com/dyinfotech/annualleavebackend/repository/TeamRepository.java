package com.dyinfotech.annualleavebackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
    boolean existsByProjectManagerId(Long projectManagerId);
    List<Team> findAllByTeam(String team);
	
	// XXX: Employee의 List<Team> teamData 를 대체한다
	List<Team> findAllByProjectManagerId(Long projectManagerId);
}