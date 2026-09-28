package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDate;
import java.util.List;

import com.dyinfotech.annualleavebackend.repository.projection.TeamManagerCacheRow;

public interface TeamManagerRepositoryCustom {
    List<Long> findTeamIdsByProjectManagerId(Long projectManagerId);
    boolean existsActiveManagerInTeam(Long teamId, LocalDate date);
    boolean existsOtherActiveManagerInTeam(Long teamId, Long employeeId, LocalDate date);
    List<TeamManagerCacheRow> findAllForCache();
    List<TeamManagerCacheRow> findAllByTeamIdForCache(Long teamId);
}
