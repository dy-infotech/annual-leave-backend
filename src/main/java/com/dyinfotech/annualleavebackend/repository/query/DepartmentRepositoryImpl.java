package com.dyinfotech.annualleavebackend.repository.query;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import com.dyinfotech.annualleavebackend.domain.QDepartment;
import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DepartmentRepositoryImpl implements DepartmentRepositoryCustom {
    private final JPAQueryFactory queryFactory;
    private static final QDepartment qDepartment = QDepartment.department;

    @Override
    public List<DepartmentCacheRow> findAllEnabledForCache() {
        return queryFactory.select(Projections.constructor(DepartmentCacheRow.class,
                        qDepartment.departmentId, qDepartment.departmentName, qDepartment.enabled))
                .from(qDepartment)
                .where(qDepartment.enabled.isTrue())
                .fetch();
    }

    @Override
    public Optional<DepartmentCacheRow> findByNameEnabledForCache(String departmentName) {
        return Optional.ofNullable(queryFactory.select(Projections.constructor(DepartmentCacheRow.class,
                        qDepartment.departmentId, qDepartment.departmentName, qDepartment.enabled))
                .from(qDepartment)
                .where(qDepartment.departmentName.eq(departmentName), qDepartment.enabled.isTrue())
                .fetchOne());
    }
}
