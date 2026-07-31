package com.dyinfotech.annualleavebackend.repository.query;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.dyinfotech.annualleavebackend.domain.QTeam;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TeamRepositoryImpl implements TeamRepositoryCustom {
    private final JPAQueryFactory queryFactory;
    private final QTeam team = QTeam.team1;
    
    @Override
    public List<Long> findAllProjectManagerIds() {
        return findAllProjectManagerIdsByEmployeeIds(null);
    }
    
	@Override
	public List<Long> findAllProjectManagerIdsByEmployeeIds(Collection<Long> employeeIds) {
	    JPAQuery<Long> query = queryFactory.select(team.projectManager.employeeId)
											.distinct()
							                .from(team);
	    
	    if (employeeIds != null && !employeeIds.isEmpty()) {
	    	query.where(team.projectManager.employeeId.in(employeeIds));
	    }
	    
		return query.fetch();
	}

}
