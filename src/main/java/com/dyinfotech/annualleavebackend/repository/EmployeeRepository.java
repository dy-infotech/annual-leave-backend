package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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

    Optional<Employee> findByEmployeeNumberAndEmail(String employeeNumber, String email);
}
