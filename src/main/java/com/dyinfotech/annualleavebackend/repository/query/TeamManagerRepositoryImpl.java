package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.dyinfotech.annualleavebackend.domain.QEmployee;
import com.dyinfotech.annualleavebackend.domain.QTeamManager;
import com.dyinfotech.annualleavebackend.repository.projection.TeamManagerCacheRow;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TeamManagerRepositoryImpl implements TeamManagerRepositoryCustom {
    private final JPAQueryFactory queryFactory;
    private static final QTeamManager qTeamManager = QTeamManager.teamManager;
    private static final QEmployee qProjectManager = new QEmployee("projectManager");

    private BooleanExpression managerActiveAt(LocalDate date) {
        return qProjectManager.hireDate.loe(date)
                .and(qProjectManager.fireDate.isNull().or(qProjectManager.fireDate.goe(date)));
    }

    @Override
    public List<Long> findTeamIdsByProjectManagerId(Long projectManagerId) {
        return queryFactory.select(qTeamManager.team.teamId)
                .from(qTeamManager)
                .where(qTeamManager.projectManager.employeeId.eq(projectManagerId))
                .fetch();
    }

    @Override
    public boolean existsActiveManagerInTeam(Long teamId, LocalDate date) {
        return queryFactory.selectOne()
                .from(qTeamManager)
                .join(qTeamManager.projectManager, qProjectManager)
                .where(qTeamManager.team.teamId.eq(teamId), managerActiveAt(date))
                .fetchFirst() != null;
    }

    @Override
    public boolean existsOtherActiveManagerInTeam(Long teamId, Long employeeId, LocalDate date) {
        return queryFactory.selectOne()
                .from(qTeamManager)
                .join(qTeamManager.projectManager, qProjectManager)
                .where(
                        qTeamManager.team.teamId.eq(teamId),
                        qProjectManager.employeeId.ne(employeeId),
                        managerActiveAt(date)
                )
                .fetchFirst() != null;
    }

    @Override
    public List<TeamManagerCacheRow> findAllForCache() {
        return queryFactory.select(Projections.constructor(
                        TeamManagerCacheRow.class,
                        qTeamManager.team.teamId,
                        qProjectManager.employeeId,
                        qTeamManager.parentTeam.teamId,
                        qProjectManager.employeeNumber,
                        qProjectManager.name,
                        qProjectManager.position,
                        qProjectManager.hireDate,
                        qProjectManager.fireDate
                ))
                .from(qTeamManager)
                .join(qTeamManager.projectManager, qProjectManager)
                .fetch();
    }

    @Override
    public List<TeamManagerCacheRow> findAllByTeamIdForCache(Long teamId) {
        return queryFactory.select(Projections.constructor(
                        TeamManagerCacheRow.class,
                        qTeamManager.team.teamId,
                        qProjectManager.employeeId,
                        qTeamManager.parentTeam.teamId,
                        qProjectManager.employeeNumber,
                        qProjectManager.name,
                        qProjectManager.position,
                        qProjectManager.hireDate,
                        qProjectManager.fireDate
                ))
                .from(qTeamManager)
                .join(qTeamManager.projectManager, qProjectManager)
                .where(qTeamManager.team.teamId.eq(teamId))
                .fetch();
    }
}
