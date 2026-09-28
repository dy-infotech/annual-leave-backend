package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.query.EmployeeRepositoryCustom;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, EmployeeRepositoryCustom {
    Optional<Employee> findByEmployeeNumber(String employeeNumber);
    Optional<Employee> findFirstByEmployeeNumberStartingWithOrderByEmployeeNumberDesc(String prefix);
    List<Employee> findAllByEmployeeIdInOrderByEmployeeIdAsc(Collection<Long> employeeIds);
    List<Employee> findAllByFireDateIsNullOrFireDateGreaterThanEqual(LocalDate now);
    List<Employee> findAllByTeam_TeamId(Long teamId);

    @Query("select count(e) > 0 from Employee e where e.team.teamId = :teamId "
            + "and (e.fireDate is null or e.fireDate >= :today)")
    boolean existsActiveEmployeeInTeam(@Param("teamId") Long teamId, @Param("today") LocalDate today);

    @Query("select count(e) > 0 from Employee e where e.team.teamId = :teamId "
            + "and e.employeeId <> :employeeId "
            + "and (e.fireDate is null or e.fireDate >= :date)")
    boolean existsActiveEmployeeInTeamExcludingEmployee(
            @Param("teamId") Long teamId,
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.employeeId = :employeeId")
    Optional<Employee> findByIdForUpdate(@Param("employeeId") Long employeeId);

    Optional<Employee> findByEmployeeNumberAndEmail(String employeeNumber, String email);
}
