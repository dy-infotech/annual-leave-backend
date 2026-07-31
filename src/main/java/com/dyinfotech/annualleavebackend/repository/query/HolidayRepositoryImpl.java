package com.dyinfotech.annualleavebackend.repository.query;

import java.time.LocalDate;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.domain.QHoliday;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class HolidayRepositoryImpl implements HolidayRepositoryCustom {
	private final JPAQueryFactory queryFactory;
	private final EntityManager entityManager;

    private static final QHoliday qHoliday = QHoliday.holiday;

    @Override
    @Transactional
    public void deleteByHolidayDateBetween(LocalDate start, LocalDate end) {
        queryFactory.delete(qHoliday)
                	.where(qHoliday.holidayDate.between(start, end))
                	.execute();
        
        entityManager.clear();
    }
}
