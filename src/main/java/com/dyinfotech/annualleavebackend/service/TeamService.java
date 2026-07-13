package com.dyinfotech.annualleavebackend.service;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamService {
	private final TeamRepository teamRepository;
	
	/**
	 * 관리자가 해당 팀을 관리하는지 정보 탐색해서 전달
	 * @param targetTeam 탐색할 팀 정보
	 * @param approverId 팀의 관리자
	 * @return Entry<Boolean, String>(isMyTeamManager, approverTeamList)
	 */
	public Map.Entry<Boolean, String> getTeamManagerData(String targetTeam, Long approverId) {
		boolean isMyTeamManager = false;
		StringBuilder teams = new StringBuilder();
		
		List<Team> teamList = teamRepository.findAllByProjectManagerId(approverId);
		for (Team team : teamList) {
			if (team.getTeam().equals(targetTeam)) {
				isMyTeamManager = true;
				break;
			}
			teams.append(team.getTeam()).append(",");
		}
		int length = teams.length();
		if (length > 0) {
			teams.setLength(length - 1);
		}
		
		return new AbstractMap.SimpleEntry<>(isMyTeamManager, teams.toString());
	}
	
	public boolean isTeamManager(Long employeeId) {
		return teamRepository.existsByProjectManagerId(employeeId);
	}

	public Long resolveApproverId(Employee employee) {
		Team myTeam = teamRepository.findFirstByTeam(employee.getTeam())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "팀 정보를 찾을 수 없습니다."));

		if (employee.getEmployeeId().equals(myTeam.getProjectManagerId())) {
			String parentTeam = myTeam.getParentTeam();
			if (parentTeam == null || parentTeam.isBlank()) {
				return null;
			}

			Team parent = teamRepository.findFirstByTeam(parentTeam)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상위 팀 정보를 찾을 수 없습니다."));
			return parent.getProjectManagerId();
		}

		return myTeam.getProjectManagerId();
	}
}
