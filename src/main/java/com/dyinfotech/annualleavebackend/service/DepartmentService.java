package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.cache.OrganizationCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.type.DepartmentType;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.config.CacheConfig.OrganizationCacheKey;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.DepartmentRepository;
import com.dyinfotech.annualleavebackend.repository.TeamRepository;
import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;
import com.github.benmanes.caffeine.cache.LoadingCache;

@Service
public class DepartmentService {

    @Qualifier("departmentLoadingCache")
    private final LoadingCache<OrganizationCacheKey, List<DepartmentCacheRow>> departmentCache;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;
    private final TeamService teamService;
    private final OrganizationCacheInvalidator cacheInvalidator;
    private final Clock clock;

    public DepartmentService(
            @Qualifier("departmentLoadingCache") LoadingCache<OrganizationCacheKey, List<DepartmentCacheRow>> departmentCache,
            DepartmentRepository departmentRepository,
            TeamRepository teamRepository,
            EmployeeRepository employeeRepository,
            TeamService teamService,
            OrganizationCacheInvalidator cacheInvalidator,
            Clock clock) {
        this.departmentCache = departmentCache;
        this.departmentRepository = departmentRepository;
        this.teamRepository = teamRepository;
        this.employeeRepository = employeeRepository;
        this.teamService = teamService;
        this.cacheInvalidator = cacheInvalidator;
        this.clock = clock;
    }

    public Optional<DepartmentCacheRow> findCachedByDepartmentName(String departmentName) {
        if (departmentName == null || departmentName.isBlank()) {
            return Optional.empty();
        }
        return departmentCache.get(OrganizationCacheKey.byName(departmentName)).stream().findFirst();
    }

    public List<DepartmentCacheRow> findAll() {
        return departmentCache.get(OrganizationCacheKey.allRows());
    }

    /**
     * 쓰기 로직에서 실제 JPA 엔티티가 필요할 때만 조회한다.
     * 부서 존재 여부와 목록 표시는 Caffeine snapshot을 사용한다.
     */
    public Optional<Department> findByDepartmentName(String departmentName) {
        return findCachedByDepartmentName(departmentName)
                .flatMap(row -> departmentRepository.findById(row.departmentId()));
    }

    public List<DepartmentCacheRow> findAllForAdmin() {
        return findAll();
    }

    private void requireCurrentPersonnelAuthorityForWrite(Long requesterId) {
        Employee requester = employeeRepository.findByIdForUpdate(requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 관리자입니다."));
        if (!requester.isActive(LocalDate.now(clock)) || !requester.hasPersonnelAuthority()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "현재 인사권이 없습니다.");
        }
    }

    @Transactional
    // 조직 변경을 잠근 뒤 현재 인사권을 확인한다
    public Long createDepartment(Long requesterId, String departmentName) {
        teamService.lockHierarchyForUpdate();
        requireCurrentPersonnelAuthorityForWrite(requesterId);

        String name = departmentName.trim();
        if (departmentRepository.findByDepartmentName(name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 부서명입니다.");
        }

        Department department = Department.builder()
                .departmentName(name)
                .enabled(Boolean.TRUE)
                .build();

        try {
            departmentRepository.saveAndFlush(department);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 부서명입니다.");
        }

        cacheInvalidator.afterDepartmentChange(Set.of(name), false);
        return department.getDepartmentId();
    }

    @Transactional
    // 대상 부서를 잠근 뒤 이름 변경 조건을 확인한다
    public void renameDepartment(Long requesterId, Long departmentId, String departmentName) {
        Department department = departmentRepository.findByIdForUpdate(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "부서 정보를 찾을 수 없습니다."));
        teamService.lockHierarchyForUpdate();
        requireCurrentPersonnelAuthorityForWrite(requesterId);

        if (DepartmentType.getParentDepartmentType().getName().equals(department.getDepartmentName())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    DepartmentType.getParentDepartmentType().getName() + " 부서명은 변경할 수 없습니다.");
        }

        String name = departmentName.trim();
        String oldName = department.getDepartmentName();
        if (name.equals(oldName)) {
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

        // Employee 응답에는 부서명이 포함되므로 해당 파생 캐시는 함께 만료한다.
        cacheInvalidator.afterDepartmentChange(Set.of(oldName, name), true);
    }

    @Transactional
    // 소속 팀이 없는 부서만 비활성화한다
    public void deleteDepartment(Long requesterId, Long departmentId) {
        Department department = departmentRepository.findByIdForUpdate(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "부서 정보를 찾을 수 없습니다."));
        teamService.lockHierarchyForUpdate();
        requireCurrentPersonnelAuthorityForWrite(requesterId);
        if (!Boolean.TRUE.equals(department.getEnabled())) {
            return;
        }

        if (DepartmentType.getParentDepartmentType().getName().equals(department.getDepartmentName())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    DepartmentType.getParentDepartmentType().getName() + " 부서는 삭제할 수 없습니다.");
        }

        if (teamRepository.existsByDepartment_DepartmentIdAndEnabledTrue(departmentId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "소속된 활성 팀이 있는 부서는 삭제할 수 없습니다. 팀을 먼저 정리해주세요.");
        }

        String oldName = department.getDepartmentName();
        department.disable();
        cacheInvalidator.afterDepartmentChange(Set.of(oldName), false);
    }
}
