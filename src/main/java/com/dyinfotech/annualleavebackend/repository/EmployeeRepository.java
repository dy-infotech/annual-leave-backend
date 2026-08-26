package com.dyinfotech.annualleavebackend.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.query.EmployeeRepositoryCustom;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, EmployeeRepositoryCustom {
    Optional<Employee> findByEmployeeNumber(String employeeNumber);
    
    Optional<Employee> findFirstByEmployeeNumberStartingWithOrderByEmployeeNumberDesc(String prefix);
    
    List<Employee> findAllByEmployeeIdInOrderByEmployeeIdAsc(Collection<Long> employeeIds);
    
    List<Employee> findAllByTeam_TeamId(Long teamId);
    
    // 재직 중인(퇴사일 없음 또는 미래) 소속 사원 존재 여부
    @Query("SELECT COUNT(e) > 0 FROM Employee e WHERE e.team.teamId = :teamId AND (e.fireDate IS NULL OR e.fireDate > :today)")
    boolean existsActiveEmployeeInTeam(@Param("teamId") Long teamId, @Param("today") LocalDate today);
    
    @Cacheable(value = CacheConfig.CACHE_EMPLOYEES, key = "'active'")
    List<Employee> findAllByFireDateIsNull();

    Optional<Employee> findByEmployeeNumberAndEmail(String employeeNumber, String email);
    
//    //사용자 정보 조건 조회
//    @Query("SELECT e FROM Employee e " +
//	       "WHERE e.employeeNumber LIKE CONCAT('%', :searchParam, '%') " +
//	       "OR e.name LIKE CONCAT('%', :searchParam, '%') " +
//	       "ORDER BY e.employeeNumber DESC")
//    List<Employee> findAllEmployees(@Param("searchParam") String searchParam);
//    
//    //사용자 정보 전체 조회(파라미터 없는 경우)
//    @Query("SELECT e FROM Employee e ORDER BY e.employeeNumber DESC")
//    List<Employee> findAllEmployees();

}