package com.dyinfotech.annualleavebackend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.repository.DepartmentRepository;
import com.github.benmanes.caffeine.cache.LoadingCache;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DepartmentService {
	@Qualifier("departmentLoadingCache")
	private final LoadingCache<String, List<Department>> departmentCache;
	private final DepartmentRepository departmentRepository;

	public DepartmentService(@Qualifier("departmentLoadingCache") LoadingCache<String, List<Department>> departmentCache, 
							DepartmentRepository departmentRepository) {
		this.departmentCache = departmentCache;
        this.departmentRepository = departmentRepository;
    }
	
	public Optional<Department> findByDepartmentName(String department) {
		return departmentCache.get(department).stream().findFirst();
	}
	
	public List<Department> findAll() {
	    return departmentCache.get(CacheConfig.TOTAL_KEY);
	}
	
	@Transactional
	public void saveDepartment(Department department) {
		departmentRepository.save(department);
		invalidateTeamCache();
	}
	
	@Transactional
	public void deleteDepartment(Department department) {
		department.disable();
		departmentRepository.flush();
		invalidateTeamCache();
	}
	
	private void invalidateTeamCache() {
		departmentCache.invalidateAll();
	}
}
