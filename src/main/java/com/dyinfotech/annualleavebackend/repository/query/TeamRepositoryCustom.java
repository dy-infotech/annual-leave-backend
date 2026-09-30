package com.dyinfotech.annualleavebackend.repository.query;

import java.util.List;
import java.util.Optional;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow;

public interface TeamRepositoryCustom {
    Optional<Team> findByIdForUpdate(Long teamId);
    List<TeamCacheRow> findAllEnabledForCache();
    Optional<TeamCacheRow> findByNameEnabledForCache(String teamName);
}
