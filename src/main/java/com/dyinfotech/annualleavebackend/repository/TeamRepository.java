package com.dyinfotech.annualleavebackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyinfotech.annualleavebackend.domain.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
	List<Team> findAllByEnabledTrue();
	Optional<Team> findByTeamName(String teamName);
	Optional<Team> findByTeamNameAndEnabledTrue(String teamName);
	boolean existsByDepartment_DepartmentIdAndEnabledTrue(Long departmentId);
}