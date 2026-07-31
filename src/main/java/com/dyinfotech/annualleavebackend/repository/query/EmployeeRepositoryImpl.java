package com.dyinfotech.annualleavebackend.repository.query;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.QEmployee;
import com.dyinfotech.annualleavebackend.repository.projection.EmployeeNumberEmail;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

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
                ? qEmployee.team.eq(team)
                : null;
    }
    
    @Override
    public List<String> findEmailsByName(String name) {
    	return queryFactory.select(qEmployee.email)
    						.from(qEmployee)
							.where(qEmployee.name.eq(name))
							.fetch();
    }
    
    @Override
    public List<String> findEmailsByEmployeeNumber(String employeeName) {
        return queryFactory.select(qEmployee.email)
        					.from(qEmployee)
							.where(qEmployee.employeeNumber.eq(employeeName))
							.fetch();
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
