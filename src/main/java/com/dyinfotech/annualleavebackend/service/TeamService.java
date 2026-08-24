package com.dyinfotech.annualleavebackend.service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.ManageType;
import com.dyinfotech.annualleavebackend.common.type.PositionType;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.repository.TeamManagerRepository;
import com.dyinfotech.annualleavebackend.repository.TeamRepository;
import com.github.benmanes.caffeine.cache.LoadingCache;

@Service
public class TeamService {
	@Qualifier("teamLoadingCache")
	private final LoadingCache<String, List<Team>> teamCache;
	@Qualifier("teamManagerLoadingCache")
	private final LoadingCache<String, List<TeamManager>> teamManagerCache;
	private final TeamRepository teamRepository;
	private final TeamManagerRepository teamManagerRepository;
	
	public TeamService(@Qualifier("teamLoadingCache") LoadingCache<String, List<Team>> teamCache, 
						@Qualifier("teamManagerLoadingCache") LoadingCache<String, List<TeamManager>> teamManagerCache, 
						TeamRepository teamRepository,
						TeamManagerRepository teamManagerRepository) {
		this.teamCache = teamCache;
        this.teamManagerCache = teamManagerCache;
        this.teamRepository = teamRepository;
        this.teamManagerRepository = teamManagerRepository;
    }
	
	public Optional<Team> findByTeamName(String team) {
		return teamCache.get(team).stream().findFirst();
	}
	
	public List<TeamManager> findAllByTeam(String team) {
		return teamManagerCache.get(team);
	}
	
	public List<TeamManager> findAll() {
	    return teamManagerCache.get(CacheConfig.TEAM_TOTAL_KEY);
	}
	
	public Set<Long> findAllProjectManagerIds() {
        return findAll().stream()
                .map(team -> team.getProjectManager().getEmployeeId())
                .collect(Collectors.toSet());
    }
	
	public Set<Long> findAllProjectManagerIds(Collection<Long> employeeIds) {
		if (employeeIds == null || employeeIds.isEmpty()) {
            return Set.of();
        }

        return findAll().stream()
                .map(TeamManager::getProjectManager)
                .map(Employee::getEmployeeId)
                .filter(employeeIds::contains)
                .collect(Collectors.toSet());
    }
	
	public boolean existsByProjectManager_EmployeeId(Long projectManagerId) {
		return teamManagerRepository.existsByProjectManager_EmployeeId(projectManagerId);
	}
	/**
	 * 기준 팀부터 시작하여 자기 자신과 모든 하위 팀 목록을 재귀적으로 수집합니다 (DFS).
	 *
	 * @param currentTeam      현재 탐색 중인 팀
	 * @param parentToChildren 부모 팀별 자식 팀 목록 Map
	 * @param result           수집된 팀 목록
	 * @param visited          순환 참조 방지용 방문 기록 Set
	 */
	private void traverseDown(TeamManager currentTeam,
					        Map<String, Set<TeamManager>> parentToChildren,
					        Set<TeamManager> result,
					        Set<String> visited) {
	    // 이미 방문한 팀이면 종료
	    if (!visited.add(currentTeam.getTeam().getTeamName())) {
	        return;
	    }

	    // 자기 자신 추가
	    result.add(currentTeam);

	    // 현재 팀의 직속 자식 조회
	    Set<TeamManager> children = parentToChildren.getOrDefault(currentTeam.getTeam(), Collections.emptySet());

	    // 하위 탐색
	    for (TeamManager child : children) {
	        traverseDown(child, parentToChildren, result, visited);
	    }
	}
	/**
	 * targetTeam부터 시작하여 자기 자신과 모든 하위 팀을 반환합니다.
	 *
	 * @param targetTeam 시작 기준 팀명
	 * @return 자기 자신 + 모든 하위 Team
	 */
	public Set<TeamManager> getSelfAndDescendants(String targetTeam) {
		List<TeamManager> allTeams = findAll();
		
		// parentTeam -> children 구성
		Map<String, Set<TeamManager>> parentToChildren = new HashMap<>();
		
		TeamManager rootTeam = null;
		for (TeamManager team : allTeams) {
			if (team.getTeam().getTeamName().equals(targetTeam)) {
				rootTeam = team;
			}
			
			parentToChildren.computeIfAbsent(team.getParentTeam().getTeamName(), k -> new HashSet<>())
							.add(team);
		}
		
		// 대상 팀 없음
		if (rootTeam == null) {
			return Collections.emptySet();
		}
		
		Set<TeamManager> result = new HashSet<>();
		traverseDown(rootTeam, parentToChildren, result, new HashSet<>());
		return result;
	}
    
    
    /**
     * targetTeam부터 최상위 루트 팀까지 역추적하여 모든 조상(본인 포함) 팀 목록을 반환합니다.
     */
    private List<String> getAllAncestors(String targetTeam) {
        List<String> ancestors = new ArrayList<>();
        String currentTeamName = targetTeam;
        Set<String> visited = new HashSet<>(); // 순환 참조(Infinite Loop) 발생 방지용 안전장치
        
        while (currentTeamName != null && !currentTeamName.isEmpty() && !currentTeamName.equals("NONE")) {
            if (!visited.add(currentTeamName)) {
                break;
            }
            ancestors.add(currentTeamName);

            // 상위 팀 정보 조회
            List<TeamManager> teamManagers = findAllByTeam(currentTeamName);
            if (teamManagers.isEmpty()) {
                break;
            }
            currentTeamName = teamManagers.get(0).getParentTeam().getTeamName();
        }
        return ancestors;
    }
	
	private Map.Entry<Integer, String> getTeamManagerData(PositionType approverPosition, String targetTeam, List<TeamManager> approverTeamList) {
		int manageType = 0;
		
		boolean isCEO = PositionType.isCEO(approverPosition);
		List<TeamManager> targetTeamList = findAllByTeam(targetTeam);
		// 신규 팀인 경우
		if (targetTeamList.isEmpty()) {
			// 대표이사만 생성 가능
			if (isCEO) {
				return new AbstractMap.SimpleEntry<>(ManageType.IS_NEW_TEAM.addFlag(manageType), "");
			} else {
				return new AbstractMap.SimpleEntry<>(manageType, String.join(",", approverTeamList.stream().map(e -> e.getTeam().getTeamName()).toList()));
			}
		}
		
		// 기존 팀인 경우 (트리 탐색 적용)
	    List<String> managedTeamNames = approverTeamList.stream().map(e -> e.getTeam().getTeamName()).toList();	// 내가 PM인 팀 목록
		if (isCEO) {
			// 대표이사는 모든 팀의 PM이다
			return new AbstractMap.SimpleEntry<>(ManageType.IS_TEAM_MANAGER.addFlag(manageType), String.join(",", managedTeamNames));
		}
		
	    List<String> ancestors = getAllAncestors(targetTeam); 							// targetTeam의 모든 상위 계보
	    if (ancestors.stream().anyMatch(managedTeamNames::contains)) {					// targetTeam과 상위 팀들 내에 내가 PM인 팀이 있는가
	        manageType = ManageType.IS_TEAM_MANAGER.addFlag(manageType);				// 상위 팀의 PM도 하위 팀의 관리자로 인정
	    }
	    
	    return new AbstractMap.SimpleEntry<>(manageType, String.join(",", managedTeamNames));
	}
	/**
	 * 관리자가 해당 팀을 관리하는지, 신규 팀인지 정보 탐색해서 전달
	 * @param targetTeam 탐색할 대상 팀 이름
	 * @param approver 결재/등록 요청자
	 * @return Entry<Integer, String>(ManageType, managedTeamNames)
	 */
	@Cacheable(value = CacheConfig.CACHE_TEAM_MANAGEMENT_DATA, key = "#a0 + '-' + #a1.employeeId")
	public Map.Entry<Integer, String> getTeamManagerData(String targetTeam, Employee approver) {
		return getTeamManagerData(PositionType.getType(approver.getPosition()), targetTeam, approver.getTeams());
	}
	
	public boolean isTeamManager(Long employeeId) {
		return teamManagerRepository.existsByProjectManager_EmployeeId(employeeId);
	}


	private Set<Employee> resolveApprovers(Employee employee) {
		List<TeamManager> myTeam = findAllByTeam(employee.getTeam().getTeamName());
		if (myTeam.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "팀 정보를 찾을 수 없습니다.");
		}
		
		Set<Employee> approvers = new HashSet<>();
		for (TeamManager team : myTeam) {
			if (employee.getEmployeeId().equals(team.getProjectManagerId())) {
				String parent = team.getParentTeam().getTeamName();
				if (parent == null || parent.isBlank()) {
					return Collections.emptySet();
				}
				
				List<TeamManager> parentTeams = parent.equals(employee.getTeam().getTeamName()) ? myTeam : findAllByTeam(parent);
				if (parentTeams.isEmpty()) {
					throw new ResponseStatusException(HttpStatus.NOT_FOUND, "상위 팀 정보를 찾을 수 없습니다.");
				}
				
				Set<Employee> parentApprovers = new HashSet<>();
				for (TeamManager parentTeam : parentTeams) {
					parentApprovers.add(parentTeam.getProjectManager());
				}
				
				return parentApprovers;
			}
			approvers.add(team.getProjectManager());
		}
		
		return approvers;
	}
	public Set<Long> refreshApproverIds(Employee employee) {
        boolean hasApproverId = false;
        Set<Employee> resolvedApprovers = resolveApprovers(employee);
        for (Employee resolvedApprover : resolvedApprovers) {
        	if (resolvedApprover.getEmployeeId().equals(employee.getApproverId())) {
        		hasApproverId = true;
        		break;
        	}
        }
        // !resolvedApproverIds.isEmpty()는 로그인시 방어코드로 유효하므로 조건 삭제 금지
        if (!hasApproverId && !resolvedApprovers.isEmpty()) {
        	// 팀 정보에 맞는 승인자로 변경하는 방어코드이므로 삭제 금지 (삭제 시 푸시 알림 고장 가능성 높음)
        	employee.changeApprover(resolvedApprovers.iterator().next());
        }
        
        return resolvedApprovers.stream().map(Employee::getEmployeeId).collect(Collectors.toSet());
	}
	
	@Transactional
	@CacheEvict(value = CacheConfig.CACHE_TEAM_MANAGEMENT_DATA, allEntries = true)
	public void saveTeam(TeamManager team) {
		teamManagerRepository.save(team);
		invalidateTeamManagerCache();
	}
	
	@Transactional
	public void saveTeam(Team team) {
		teamRepository.save(team);
		invalidateTeamCache();
	}
	
	public void deleteTeam(TeamManager team) {
		teamManagerRepository.delete(team);
		invalidateTeamManagerCache();
	}
	
	@Transactional
	public void deleteTeam(Team team) {
		team.disable();
		teamRepository.flush();
		invalidateTeamCache();
	}
	
	private void invalidateTeamCache() {
		teamCache.invalidateAll();
	}
	
	private void invalidateTeamManagerCache() {
		teamManagerCache.invalidateAll();
	}
}
