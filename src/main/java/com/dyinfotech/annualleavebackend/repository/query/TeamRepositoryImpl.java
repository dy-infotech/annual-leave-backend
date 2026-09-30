package com.dyinfotech.annualleavebackend.repository.query;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import com.dyinfotech.annualleavebackend.domain.QTeam;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TeamRepositoryImpl implements TeamRepositoryCustom {
    private final JPAQueryFactory queryFactory;
    private static final QTeam qTeam = QTeam.team;

    @Override
    public Optional<Team> findByIdForUpdate(Long teamId) {
        return Optional.ofNullable(queryFactory.selectFrom(qTeam)
                .where(qTeam.teamId.eq(teamId))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne());
    }

    @Override
    public List<TeamCacheRow> findAllEnabledForCache() {
        return queryFactory.select(Projections.constructor(TeamCacheRow.class,
                        qTeam.teamId, qTeam.teamName, qTeam.department.departmentId, qTeam.enabled))
                .from(qTeam)
                .where(qTeam.enabled.isTrue())
                .fetch();
    }

    @Override
    public Optional<TeamCacheRow> findByNameEnabledForCache(String teamName) {
        return Optional.ofNullable(queryFactory.select(Projections.constructor(TeamCacheRow.class,
                        qTeam.teamId, qTeam.teamName, qTeam.department.departmentId, qTeam.enabled))
                .from(qTeam)
                .where(qTeam.teamName.eq(teamName), qTeam.enabled.isTrue())
                .fetchOne());
    }
}
