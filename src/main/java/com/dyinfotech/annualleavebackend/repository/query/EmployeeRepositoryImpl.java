package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.dyinfotech.annualleavebackend.common.IpContext;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.QEmployee;
import com.dyinfotech.annualleavebackend.repository.projection.EmployeeNumberEmail;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class EmployeeRepositoryImpl implements EmployeeRepositoryCustom {
	
	private final JPAQueryFactory queryFactory;

    private static final QEmployee qEmployee = QEmployee.employee;

	@Override
	public List<Employee> findAllEmployees(String searchParam, String team) {
		// TODO Auto-generated method stub
		return queryFactory.selectFrom(qEmployee)
                            .join(qEmployee.department).fetchJoin()
                            .join(qEmployee.team).fetchJoin()
			                .where(
			                    searchCondition(searchParam),
			                    teamCondition(team)
			                )
			                .orderBy(qEmployee.employeeNumber.desc())
			                .fetch();
	}
	
	private BooleanExpression searchCondition(String searchParam) {
        if (searchParam == null || searchParam.isBlank()) {
            return null;
        }

        return qEmployee.employeeNumber.contains(searchParam)
                .or(qEmployee.name.contains(searchParam));
    }


    private BooleanExpression teamCondition(String team) {
        return team != null && !team.isBlank()
                ? qEmployee.team.teamName.eq(team)
                : null;
    }

    private BooleanExpression activeAt(LocalDate date) {
        return qEmployee.hireDate.loe(date)
                .and(qEmployee.fireDate.isNull().or(qEmployee.fireDate.goe(date)));
    }

    @Override
    public List<Employee> findAllActiveAt(LocalDate date) {
        return queryFactory.selectFrom(qEmployee)
                .where(activeAt(date))
                .orderBy(qEmployee.employeeId.asc())
                .fetch();
    }

    @Override
    public boolean existsActiveEmployeeInTeam(Long teamId, LocalDate date) {
        return queryFactory.selectOne()
                .from(qEmployee)
                .where(qEmployee.team.teamId.eq(teamId), activeAt(date))
                .fetchFirst() != null;
    }

    @Override
    public boolean existsActiveEmployeeInTeamExcludingEmployee(Long teamId, Long employeeId, LocalDate date) {
        return queryFactory.selectOne()
                .from(qEmployee)
                .where(
                        qEmployee.team.teamId.eq(teamId),
                        qEmployee.employeeId.ne(employeeId),
                        activeAt(date)
                )
                .fetchFirst() != null;
    }
    
    @Override
    public Optional<Employee> findByIdForUpdate(Long employeeId) {
        return Optional.ofNullable(
                queryFactory.selectFrom(qEmployee)
                        .where(qEmployee.employeeId.eq(employeeId))
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .fetchOne()
        );
    }
    
    @Override
    public long increaseAccessCount(Long employeeId, LocalDateTime now) {
        return queryFactory.update(qEmployee)
			                .set(qEmployee.accessCount, qEmployee.accessCount.add(1))
			                .set(qEmployee.accessedAt, now)
			                .set(qEmployee.accessedIp, IpContext.get())
			                .where(qEmployee.employeeId.eq(employeeId))
			                .execute();
    }

    @Override
    public long resetAccessCount(Long employeeId, LocalDateTime now) {
        return queryFactory.update(qEmployee)
			                .set(qEmployee.accessCount, 0)
			                .set(qEmployee.accessedAt, now)
			                .set(qEmployee.accessedIp, IpContext.get())
			                .where(qEmployee.employeeId.eq(employeeId))
			                .execute();
    }
    
    @Override
    public long updatePassword(Long employeeId, String password) {
        return queryFactory.update(qEmployee)
			                .set(qEmployee.password, password)
			                .where(qEmployee.employeeId.eq(employeeId))
			                .execute();
    }
    
    @Override
    public long compareAndSetPassword(Long employeeId, String expectedPassword, String newPassword) {
        return queryFactory.update(qEmployee)
                .set(qEmployee.password, newPassword)
                .where(
                        qEmployee.employeeId.eq(employeeId),
                        qEmployee.password.eq(expectedPassword)
                )
                .execute();
    }

    @Override
    public long updateCurrTotalLeaveDays(Long employeeId, float days) {
        return queryFactory.update(qEmployee)
			                .set(qEmployee.currTotalLeaveDays, days)
			                .where(qEmployee.employeeId.eq(employeeId))
			                .execute();
    }
    
    @Override
    public List<String> findEmailsByName(String name) {
    	return queryFactory.select(qEmployee.email)
    						.from(qEmployee)
							.where(qEmployee.name.eq(name))
							.fetch();
    }
    
    @Override
    public String findEmailsByEmployeeNumber(String employeeName) {
        return queryFactory.select(qEmployee.email)
        					.from(qEmployee)
							.where(qEmployee.employeeNumber.eq(employeeName))
							.fetchOne();
    }

	@Override
	public List<EmployeeNumberEmail> findEmployeeNumberAndEmailByNameAndEmailIn(String name, List<String> emailList) {
		return queryFactory.select(Projections.constructor(
				                    EmployeeNumberEmail.class,
				                    qEmployee.employeeNumber,
				                    qEmployee.email
				            ))
				            .from(qEmployee)
				            .where(
				            		qEmployee.name.eq(name),
				            		qEmployee.email.in(emailList)
				            )
				            .fetch();
	}

}
