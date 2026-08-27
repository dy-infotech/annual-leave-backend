package com.dyinfotech.annualleavebackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.domain.TeamManager.TeamManagerId;

public interface TeamManagerRepository extends JpaRepository<TeamManager, TeamManagerId> {
	boolean existsByProjectManager_EmployeeId(Long projectManagerId);
	TeamManager findFirstByTeam_TeamNameOrderByProjectManager_EmployeeIdAsc(String team);
    List<TeamManager> findAllByTeam_TeamNameOrderByTeam_TeamIdAsc(String team);

    // 캐시 적재용 조회. 캐시된 엔티티는 세션이 닫힌 뒤에도 탐색되므로
    // LAZY 연관(team, parentTeam, projectManager)을 미리 함께 로딩해 둔다.
    // (미적용 시 LazyInitializationException — no session)
    @Query("select tm from TeamManager tm"
            + " join fetch tm.team join fetch tm.parentTeam join fetch tm.projectManager")
    List<TeamManager> findAllWithAssociations();

    @Query("select tm from TeamManager tm"
            + " join fetch tm.team t join fetch tm.parentTeam join fetch tm.projectManager"
            + " where t.teamName = :teamName order by t.teamId asc")
    List<TeamManager> findAllByTeamNameWithAssociations(@Param("teamName") String teamName);
    List<TeamManager> findAllByTeam_TeamId(Long teamId);
    boolean existsByParentTeam_TeamIdAndTeam_TeamIdNot(Long parentTeamId, Long teamId);
    long deleteByTeam_TeamId(Long teamId);
}
