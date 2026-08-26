package com.dyinfotech.annualleavebackend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.DepartmentType;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.repository.DepartmentRepository;
import com.dyinfotech.annualleavebackend.repository.TeamRepository;
import com.github.benmanes.caffeine.cache.LoadingCache;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DepartmentService {
	@Qualifier("departmentLoadingCache")
	private final LoadingCache<String, List<Department>> departmentCache;
	private final DepartmentRepository departmentRepository;
	private final TeamRepository teamRepository;

	public DepartmentService(@Qualifier("departmentLoadingCache") LoadingCache<String, List<Department>> departmentCache, 
							DepartmentRepository departmentRepository,
							TeamRepository teamRepository) {
		this.departmentCache = departmentCache;
        this.departmentRepository = departmentRepository;
        this.teamRepository = teamRepository;
    }
	
	public Optional<Department> findByDepartmentName(String department) {
		return departmentCache.get(department).stream().findFirst();
	}
	
	public List<Department> findAll() {
	    return departmentCache.get(CacheConfig.TOTAL_KEY);
	}
	
	/** 관리 화면용: 전체 조회 (소프트 딜리트된 부서 제외, 캐시 미사용) */
	public List<Department> findAllForAdmin() {
	    return departmentRepository.findAllByEnabledTrue();
	}
	
	@Transactional
	public Long createDepartment(String departmentName) {
		String name = departmentName.trim();
		if (departmentRepository.findByDepartmentName(name).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 부서명입니다.");
		}
		
		Department department = Department.builder()
											.departmentName(name)
											.enabled(Boolean.TRUE)
											.build();
		try {
			// 사전 중복 검사를 통과한 동시 요청이 UNIQUE 제약에 걸릴 수 있으므로 즉시 flush하여 409로 변환한다
			departmentRepository.saveAndFlush(department);
		} catch (DataIntegrityViolationException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 부서명입니다.");
		}
		invalidateDepartmentCache();
		return department.getDepartmentId();
	}
	
	@Transactional
	public void renameDepartment(Long departmentId, String departmentName) {
		Department department = departmentRepository.findById(departmentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "부서 정보를 찾을 수 없습니다."));
		
		// 대표이사 부서는 코드(DepartmentType)가 이름으로 식별하므로 변경을 금지한다.
		if (DepartmentType.getParentDepartmentType().getName().equals(department.getDepartmentName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DepartmentType.getParentDepartmentType().getName() + " 부서명은 변경할 수 없습니다.");
		}
		
		String name = departmentName.trim();
		if (name.equals(department.getDepartmentName())) {
			return;
		}
		if (departmentRepository.findByDepartmentName(name).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 부서명입니다.");
		}
		
		department.changeName(name);
		try {
			departmentRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 부서명입니다.");
		}
		invalidateDepartmentCache();
	}
	
	@Transactional
	public void saveDepartment(Department department) {
		departmentRepository.save(department);
		invalidateDepartmentCache();
	}
	
	/**
	 * 부서 소프트 딜리트. 대표이사 부서와 활성 팀이 소속된 부서는 삭제할 수 없다.
	 * 이미 삭제된 부서는 무동작(멱등).
	 */
	@Transactional
	public void deleteDepartment(Long departmentId) {
		Department department = departmentRepository.findById(departmentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "부서 정보를 찾을 수 없습니다."));
		if (!Boolean.TRUE.equals(department.getEnabled())) {
			return;	// 이미 삭제된 부서 (멱등 처리)
		}
		
		// 대표이사 부서는 코드(DepartmentType)가 이름으로 식별하므로 삭제할 수 없다
		if (DepartmentType.getParentDepartmentType().getName().equals(department.getDepartmentName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DepartmentType.getParentDepartmentType().getName() + " 부서는 삭제할 수 없습니다.");
		}
		
		// 소속된 활성 팀이 있으면 삭제할 수 없다 (부서:팀 = 1:N)
		if (teamRepository.existsByDepartment_DepartmentIdAndEnabledTrue(departmentId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "소속된 활성 팀이 있는 부서는 삭제할 수 없습니다. 팀을 먼저 정리해주세요.");
		}
		
		department.disable();
		invalidateDepartmentCache();
	}
	
	private void invalidateDepartmentCache() {
		departmentCache.invalidateAll();
	}
}
