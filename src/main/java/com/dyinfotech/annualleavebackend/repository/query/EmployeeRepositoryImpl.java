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

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class EmployeeRepositoryImpl implements EmployeeRepositoryCustom {
	
	private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

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

    @Override
    public List<Employee> findEmployeesPage(
            String searchParam,
            String team,
            Boolean registered,
            int page,
            int size) {
        return queryFactory.selectFrom(qEmployee)
                .join(qEmployee.department).fetchJoin()
                .join(qEmployee.team).fetchJoin()
                .where(
                        searchCondition(searchParam),
                        teamCondition(team),
                        registeredCondition(registered))
                .orderBy(qEmployee.employeeNumber.desc())
                .offset((long) page * size)
                .limit(size)
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

    private BooleanExpression registeredCondition(Boolean registered) {
        if (registered == null) {
            return null;
        }
        return registered ? qEmployee.password.isNotNull() : qEmployee.password.isNull();
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
    public List<Long> findActiveEmployeeIdsAfter(LocalDate date, Long afterEmployeeId, int limit) {
        BooleanExpression afterId = afterEmployeeId == null
                ? null
                : qEmployee.employeeId.gt(afterEmployeeId);

        return queryFactory.select(qEmployee.employeeId)
                .from(qEmployee)
                .where(activeAt(date), afterId)
                .orderBy(qEmployee.employeeId.asc())
                .limit(limit)
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
    public boolean existsEmployeeRequiringTeam(Long teamId, LocalDate date) {
        return queryFactory.selectOne()
                .from(qEmployee)
                .where(
                        qEmployee.team.teamId.eq(teamId),
                        qEmployee.fireDate.isNull().or(qEmployee.fireDate.goe(date))
                )
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
    public List<Employee> findAllByIdsForUpdate(java.util.Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return List.of();
        }
        List<Employee> employees = queryFactory.selectFrom(qEmployee)
                .where(qEmployee.employeeId.in(employeeIds))
                .orderBy(qEmployee.employeeId.asc())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetch();
        // 잠금 획득 후 직원 정보를 현재 DB 상태로 다시 읽는다
        employees.forEach(employee ->
                entityManager.refresh(employee, LockModeType.PESSIMISTIC_WRITE));
        return employees;
    }

    @Override
    public Optional<Employee> findByIdForUpdate(Long employeeId) {
        Employee employee = queryFactory.selectFrom(qEmployee)
                .where(qEmployee.employeeId.eq(employeeId))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        if (employee != null) {
            // 잠금 획득 후 직원 정보를 현재 DB 상태로 다시 읽는다
            entityManager.refresh(employee, LockModeType.PESSIMISTIC_WRITE);
        }
        return Optional.ofNullable(employee);
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
    public long completeSignUpIfUnregistered(Long employeeId, String newPassword) {
        long updated = queryFactory.update(qEmployee)
                .set(qEmployee.password, newPassword)
                .where(
                        qEmployee.employeeId.eq(employeeId),
                        qEmployee.password.isNull()
                )
                .execute();

        // 가입 처리 후 이전 영속성 상태를 비운다
        entityManager.clear();
        return updated;
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
