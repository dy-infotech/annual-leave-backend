package com.dyinfotech.annualleavebackend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.LeaveAdjustmentRepository;

class EmployeeLeaveCacheRegressionTest {

    @Test
    void yearlyRenewal_bumpsRenewedEmployeeViewGeneration() {
        BasisDataFactory basisDataFactory = mock(BasisDataFactory.class);
        LeaveAdjustmentRepository leaveAdjustmentRepository = mock(LeaveAdjustmentRepository.class);
        TeamService teamService = mock(TeamService.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        EmployeeCacheInvalidator employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any()))
                .thenReturn(mock(TransactionStatus.class));
        Clock clock = Clock.fixed(
                Instant.parse("2026-01-01T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        EmployeeLeaveService service = spy(new EmployeeLeaveService(
                basisDataFactory,
                leaveAdjustmentRepository,
                teamService,
                employeeRepository,
                employeeCacheInvalidator,
                transactionManager,
                clock
        ));

        Employee employee = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(1L);
        when(employee.getEmployeeNumber()).thenReturn("E0001");
        when(employee.getCurrYear()).thenReturn("2025");
        when(employee.getCurrTotalLeaveDays()).thenReturn(15.0f);
        when(employee.isActive(any(LocalDate.class))).thenReturn(true);
        when(employeeRepository.findActiveEmployeeIdsAfter(
                any(LocalDate.class), any(), any(Integer.class)))
                .thenReturn(List.of(1L), List.of());
        when(employeeRepository.findAllByIdsForUpdate(List.of(1L)))
                .thenReturn(List.of(employee));
        doReturn(15.0f).when(service).getCalculatedCurrYearLeaveDays(employee);

        service.renewAllActiveEmployeesLeave("2026");

        verify(employee).setPrevYear("2025");
        verify(employee).setPrevYearLeaveDays(15.0f);
        verify(employee).setCurrYear("2026");
        verify(employee).setCurrYearLeaveDays(15.0f);
        verify(employeeCacheInvalidator).afterEmployeeViewChange(
                org.mockito.ArgumentMatchers.<java.util.Collection<Long>>argThat(
                        ids -> ids != null && ids.size() == 1 && ids.contains(1L))
        );
    }
    @Test
    void yearlyRenewal_batchCommitFailure_retriesEmployeesIndividually() {
        BasisDataFactory basisDataFactory = mock(BasisDataFactory.class);
        LeaveAdjustmentRepository leaveAdjustmentRepository = mock(LeaveAdjustmentRepository.class);
        TeamService teamService = mock(TeamService.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        EmployeeCacheInvalidator employeeCacheInvalidator = mock(EmployeeCacheInvalidator.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        doThrow(new RuntimeException("batch commit failed"))
                .doNothing()
                .doNothing()
                .when(transactionManager)
                .commit(any());

        Clock clock = Clock.fixed(
                Instant.parse("2026-01-01T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        EmployeeLeaveService service = spy(new EmployeeLeaveService(
                basisDataFactory,
                leaveAdjustmentRepository,
                teamService,
                employeeRepository,
                employeeCacheInvalidator,
                transactionManager,
                clock
        ));

        Employee employee1 = mock(Employee.class);
        when(employee1.getEmployeeId()).thenReturn(1L);
        when(employee1.getEmployeeNumber()).thenReturn("E0001");
        when(employee1.getCurrYear()).thenReturn("2025");
        when(employee1.getCurrTotalLeaveDays()).thenReturn(15.0f);
        when(employee1.isActive(any(LocalDate.class))).thenReturn(true);

        Employee employee2 = mock(Employee.class);
        when(employee2.getEmployeeId()).thenReturn(2L);
        when(employee2.getEmployeeNumber()).thenReturn("E0002");
        when(employee2.getCurrYear()).thenReturn("2025");
        when(employee2.getCurrTotalLeaveDays()).thenReturn(15.0f);
        when(employee2.isActive(any(LocalDate.class))).thenReturn(true);

        when(employeeRepository.findActiveEmployeeIdsAfter(
                any(LocalDate.class), any(), any(Integer.class)))
                .thenReturn(List.of(1L, 2L), List.of());
        when(employeeRepository.findAllByIdsForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(employee1, employee2));
        when(employeeRepository.findAllByIdsForUpdate(List.of(1L)))
                .thenReturn(List.of(employee1));
        when(employeeRepository.findAllByIdsForUpdate(List.of(2L)))
                .thenReturn(List.of(employee2));
        doReturn(15.0f).when(service).getCalculatedCurrYearLeaveDays(any(Employee.class));

        service.renewAllActiveEmployeesLeave("2026");

        verify(employeeRepository).findAllByIdsForUpdate(List.of(1L, 2L));
        verify(employeeRepository).findAllByIdsForUpdate(List.of(1L));
        verify(employeeRepository).findAllByIdsForUpdate(List.of(2L));
        verify(transactionManager, times(3)).commit(any());
    }

}
