package com.dyinfotech.annualleavebackend.service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.dyinfotech.annualleavebackend.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamService {
	private final TeamRepository teamRepository;
	
	@Cacheable(value = CacheConfig.CACHE_TEAMS, key = "#a0")
	public List<Team> findAllByTeam(String team) {
		return teamRepository.findAllByTeamOrderBySeqAsc(team);
	}
	
	@Cacheable(value = CacheConfig.CACHE_TEAMS, key = "'total'")
	public List<Team> findAll() {
		return teamRepository.findAll();
	}
	/**
	 * 기준 팀부터 시작하여 자기 자신과 모든 하위 팀 목록을 재귀적으로 수집합니다 (DFS).
	 *
	 * @param currentTeam      현재 탐색 중인 팀
	 * @param parentToChildren 부모 팀별 자식 팀 목록 Map
	 * @param result           수집된 팀 목록
	 * @param visited          순환 참조 방지용 방문 기록 Set
	 */
	private void traverseDown(Team currentTeam,
					        Map<String, Set<Team>> parentToChildren,
					        Set<Team> result,
					        Set<String> visited) {
	    // 이미 방문한 팀이면 종료
	    if (!visited.add(currentTeam.getTeam())) {
	        return;
	    }

	    // 자기 자신 추가
	    result.add(currentTeam);

	    // 현재 팀의 직속 자식 조회
	    Set<Team> children = parentToChildren.getOrDefault(currentTeam.getTeam(), Collections.emptySet());

	    // 하위 탐색
	    for (Team child : children) {
	        traverseDown(child, parentToChildren, result, visited);
	    }
	}
	/**
	 * targetTeam부터 시작하여 자기 자신과 모든 하위 팀을 반환합니다.
	 *
	 * @param targetTeam 시작 기준 팀명
	 * @return 자기 자신 + 모든 하위 Team
	 */
	public Set<Team> getSelfAndDescendants(String targetTeam) {
		List<Team> allTeams = findAll();
		
		// parentTeam -> children 구성
		Map<String, Set<Team>> parentToChildren = new HashMap<>();
		
		Team rootTeam = null;
		for (Team team : allTeams) {
			if (team.getTeam().equals(targetTeam)) {
				rootTeam = team;
			}
			
			parentToChildren.computeIfAbsent(team.getParentTeam(), k -> new HashSet<>())
							.add(team);
		}
		
		// 대상 팀 없음
		if (rootTeam == null) {
			return Collections.emptySet();
		}
		
		Set<Team> result = new HashSet<>();
		traverseDown(rootTeam, parentToChildren, result, new HashSet<>());
		return result;
	}
	
    
//    /**
//     * 기준 팀부터 시작하여 자기 자신과 모든 하위 팀 목록을 재귀적으로 수집합니다 (DFS).
//     *
//     * @param currentTeam      현재 탐색 중인 팀 이름
//     * @param parentToChildren 부모 팀별 자식 팀 목록 Map
//     * @param result           수집된 팀 List
//     * @param visited          순환 참조 방지용 방문 기록 Set
//     */
//    private void traverseDown(String currentTeam, 
//    		Map<String, Set<Team>> parentToChildren, 
//    		Set<Team> result, 
//    		Set<String> visited) {
//    	// 이미 방문한 팀이라면 중복 추가 방지 및 순환 참조 탈출
//    	if (!visited.add(currentTeam)) {
//    		return;
//    	}
//    	
//    	// 진입하자마자 자기 자신(부모)을 리스트에 바로 추가. 이후 자식들이 추가되는 구조로 바뀜
//    	result.add(currentTeam);
//    	
//    	// 내 밑에 달린 직속 자식 팀들을 획득
//    	Set<Team> children = parentToChildren.getOrDefault(currentTeam, Collections.emptySet());
//    	
//    	// 자식 팀들을 하나씩 순회하며 아래로 깊게 파고 들어감 (DFS)
//    	for (Team child : children) {
//    		traverseDown(child.getTeam(), parentToChildren, result, visited);
//    	}
//    }
//    /**
//     * targetTeam부터 시작하여 자기 자신과 모든 하위(자식/후손) 팀 목록을 전부 반환합니다.
//     * @param targetTeam 시작 기준 팀 (본인)
//     * @return 자기 자신 + 모든 하위 팀명이 담긴 리스트
//     */
//    public Set<String> getSelfAndDescendants(String targetTeam) {
//    	// 1. 팀 테이블 전체 로드
//    	List<Team> allTeams = findAll();
//    	
//    	// 2. 부모 팀 이름을 Key로 하고, 직속 자식 팀 리스트를 Value로 갖는 Map 구성
//    	Map<String, Set<Team>> parentToChildren = new HashMap<>();
//    	boolean exists = false;
//    	
//    	for (Team team : allTeams) {
//    		// 시작 대상 팀(targetTeam)이 실존하는지 체크
//    		if (!exists && team.getTeam().equals(targetTeam)) {
//    			exists = true;
//    		}
//    		
//    		// 부모 팀 이름을 Key로 하는 자식 리스트 Map 구성
//    		parentToChildren.computeIfAbsent(team.getParentTeam(), k -> new HashSet<>())
//    		.add(team);
//    	}
//    	
//    	// 시작 팀이 테이블에 실존하는지 검증
//    	Set<String> result = new HashSet<>();
//    	if (!exists) {
//    		return result; // 존재하지 않는 팀이면 빈 리스트 반환
//    	}
//    	
//    	// 3. 재귀 DFS 탐색 시작 (자기 자신부터 아래로)
//    	traverseDown(targetTeam, parentToChildren, result, new HashSet<>());
//    	
//    	return result;
//    }
    
    
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
            List<Team> teamOpt = findAllByTeam(currentTeamName);
            if (teamOpt.isEmpty()) {
                break;
            }
            currentTeamName = teamOpt.get(0).getParentTeam();
        }
        return ancestors;
    }
	
	private Map.Entry<Integer, String> getTeamManagerData(PositionType approverPosition, String targetTeam, List<Team> approverTeamList) {
		int manageType = 0;
		
		List<Team> targetTeamList = findAllByTeam(targetTeam);
		// 신규 팀인 경우
		if (targetTeamList.isEmpty()) {
			// 대표이사만 생성 가능
			if (PositionType.CEO.equals(approverPosition)) {
				return new AbstractMap.SimpleEntry<>(ManageType.IS_NEW_TEAM.getAppliedCode(manageType), "");
			} else {
				return new AbstractMap.SimpleEntry<>(manageType, String.join(",", approverTeamList.stream().map(Team::getTeam).toList()));
			}
		}
		
		// 기존 팀인 경우 (트리 탐색 적용)
	    List<String> managedTeamNames = approverTeamList.stream().map(Team::getTeam).toList();	// 내가 PM인 팀 목록
	    List<String> ancestors = getAllAncestors(targetTeam); 							// targetTeam의 모든 상위 계보
	    
	    if (ancestors.stream().anyMatch(managedTeamNames::contains)) {					// targetTeam과 상위 팀들 내에 내가 PM인 팀이 있는가
	        manageType = ManageType.IS_TEAM_MANAGER.getAppliedCode(manageType);			// 상위 팀의 PM도 하위 팀의 관리자로 인정
	    }
	    
	    return new AbstractMap.SimpleEntry<>(manageType, String.join(",", managedTeamNames));
	}
	/**
	 * 관리자가 해당 팀을 관리하는지, 신규 팀인지 정보 탐색해서 전달
	 * @param targetTeam 탐색할 대상 팀 이름
	 * @param approver 결재/등록 요청자
	 * @return Entry<Integer, String>(ManageType, managedTeamNames)
	 */
	@Cacheable(value = CacheConfig.CACHE_TEAMS, key = "#a0 + '-' + #a1.employeeId")
	public Map.Entry<Integer, String> getTeamManagerData(String targetTeam, Employee approver) {
		return getTeamManagerData(PositionType.getType(approver.getPosition()), targetTeam, approver.getTeams());
	}
	
	public boolean isTeamManager(Long employeeId) {
		return teamRepository.existsByProjectManager_EmployeeId(employeeId);
	}


	private Set<Employee> resolveApprovers(Employee employee) {
		List<Team> myTeam = findAllByTeam(employee.getTeam());
		if (myTeam.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "팀 정보를 찾을 수 없습니다.");
		}
		
		Set<Employee> approvers = new HashSet<>();
		for (Team team : myTeam) {
			if (employee.getEmployeeId().equals(team.getProjectManagerId())) {
				String parent = team.getParentTeam();
				if (parent == null || parent.isBlank()) {
					return Collections.emptySet();
				}
				
				List<Team> parentTeams = parent.equals(employee.getTeam()) ? myTeam : findAllByTeam(parent);
				if (parentTeams.isEmpty()) {
					throw new ResponseStatusException(HttpStatus.NOT_FOUND, "상위 팀 정보를 찾을 수 없습니다.");
				}
				
				Set<Employee> parentApprovers = new HashSet<>();
				for (Team parentTeam : parentTeams) {
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
	@CacheEvict(value = CacheConfig.CACHE_TEAMS, allEntries = true)
	public void saveTeam(Team team) {
		teamRepository.save(team);
	}
}
