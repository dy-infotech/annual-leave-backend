package com.dyinfotech.annualleavebackend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmployeeNumber(String employeeNumber);
//    @Deprecated
//    Optional<Employee> findByLoginId(String loginId);
//    @Deprecated
//    boolean existsByLoginId(String loginId);
    
    Optional<Employee> findFirstByEmployeeNumberStartingWithOrderByEmployeeNumberDesc(String prefix);
    
    List<Employee> findAllByEmployeeIdIn(Collection<Long> employeeIds);
    
    @Cacheable(value = CacheConfig.CACHE_EMPLOYEES, key = "'active'")
    List<Employee> findAllByFireDateIsNull();
     
    
    Optional<Employee> findEmployeeNumberByNameAndEmail(String name, String email);

    Optional<Employee> findByEmployeeNumberAndEmail(String employeeNumber, String email);
    
    //사용자 정보 조건 조회
    @Query("SELECT e FROM Employee e " +
	       "WHERE e.employeeNumber LIKE CONCAT('%', :searchParam, '%') " +
	       "OR e.name LIKE CONCAT('%', :searchParam, '%') " +
	       "ORDER BY e.employeeNumber DESC")
    List<Employee> findAllEmployeesWithSearch(@Param("searchParam") String searchParam);
    
    //사용자 정보 전체 조회(파라미터 없는 경우)
    @Query("SELECT e FROM Employee e ORDER BY e.employeeNumber DESC")
    List<Employee> findAllEmployees();

}