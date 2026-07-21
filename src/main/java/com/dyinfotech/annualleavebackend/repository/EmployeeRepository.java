package com.dyinfotech.annualleavebackend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}