package com.dyinfotech.annualleavebackend.service;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.PositionType;
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
	public Map.Entry<Boolean, String> getTeamManagerData(PositionType approverPosition, String targetTeam, Long approverId) {
		boolean isMyTeamManager = false;
		StringBuilder teams = new StringBuilder();
		
		List<Team> teamList = teamRepository.findAllByProjectManagerId(approverId);
		List<Team> targetTeamList = teamRepository.findAllByTeam(targetTeam);
		// 신규 팀인 경우
		if (targetTeamList.isEmpty()) {
			// 대표이사만 생성 가능
			if (approverPosition.equals(PositionType.CEO)) {
				return new AbstractMap.SimpleEntry<>(Boolean.TRUE, "");
			} else {
				return new AbstractMap.SimpleEntry<>(Boolean.FALSE, teamList.stream().map(Team::getTeam).toList().toString());
			}
		}
		// 기존 팀인 경우
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
		
		if (!isMyTeamManager) {
			// 상위 팀의 관리자인지 확인 (targetTeamList가 isEmpty인 경우는 early return 처리되었으므로 분기문 처리 안 함)
			String parentTeam = targetTeamList.get(0).getParentTeam();
			for (Team team : teamList) {
				if (team.getTeam().equals(parentTeam)) {
					isMyTeamManager = true;
					break;
				}
			}
		}
		
		return new AbstractMap.SimpleEntry<>(isMyTeamManager, teams.toString());
	}
	
	public boolean isTeamManager(Long employeeId) {
		return teamRepository.existsByProjectManagerId(employeeId);
	}


	private Set<Long> resolveApproverIds(Employee employee) {
		List<Team> myTeam = teamRepository.findAllByTeam(employee.getTeam());
		if (myTeam.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "팀 정보를 찾을 수 없습니다.");
		}
		
		Set<Long> approvers = new HashSet<>();
		for (Team team : myTeam) {
			if (employee.getEmployeeId().equals(team.getProjectManagerId())) {
				String parent = team.getParentTeam();
				if (parent == null || parent.isBlank()) {
					return Collections.emptySet();
				}
				
				List<Team> parentTeams = teamRepository.findAllByTeam(parent);
				if (parentTeams.isEmpty()) {
					throw new ResponseStatusException(HttpStatus.NOT_FOUND, "상위 팀 정보를 찾을 수 없습니다.");
				}
				
				Set<Long> parentApprovers = new HashSet<>();
				for (Team parentTeam : parentTeams) {
					parentApprovers.add(parentTeam.getProjectManagerId());
				}
				
				return parentApprovers;
			}
			approvers.add(team.getProjectManagerId());
		}
		
		return approvers;
	}
	public Set<Long> refreshApproverIds(Employee employee) {
        boolean hasApproverId = false;
        Set<Long> resolvedApproverIds = resolveApproverIds(employee);
        for (Long resolvedApproverId : resolvedApproverIds) {
        	if (resolvedApproverId.equals(employee.getApproverId())) {
        		hasApproverId = true;
        		break;
        	}
        }
        // !resolvedApproverIds.isEmpty()는 로그인시 방어코드로 유효하므로 조건 삭제 금지
        if (!hasApproverId && !resolvedApproverIds.isEmpty()) {
        	// 팀 정보에 맞는 승인자로 변경하는 방어코드이므로 삭제 금지 (삭제 시 푸시 알림 고장 가능성 높음)
        	employee.changeApprover(resolvedApproverIds.iterator().next());
        }
        
        return resolvedApproverIds;
	}
	
	public Collection<String> findAllTeamName() {
		return teamRepository.findAll().stream().map(Team::getTeam).toList();
	}
	
	@Transactional
	public void saveTeam(Team team) {
		teamRepository.save(team);
	}
}
