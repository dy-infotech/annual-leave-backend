package com.dyinfotech.annualleavebackend.service;

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
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.repository.DepartmentRepository;
import com.dyinfotech.annualleavebackend.repository.TeamRepository;
import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;
import com.github.benmanes.caffeine.cache.LoadingCache;

@Service
public class DepartmentService {

    @Qualifier("departmentLoadingCache")
    private final LoadingCache<String, List<DepartmentCacheRow>> departmentCache;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final OrganizationCacheInvalidator cacheInvalidator;

    public DepartmentService(
            @Qualifier("departmentLoadingCache") LoadingCache<String, List<DepartmentCacheRow>> departmentCache,
            DepartmentRepository departmentRepository,
            TeamRepository teamRepository,
            OrganizationCacheInvalidator cacheInvalidator) {
        this.departmentCache = departmentCache;
        this.departmentRepository = departmentRepository;
        this.teamRepository = teamRepository;
        this.cacheInvalidator = cacheInvalidator;
    }

    public Optional<DepartmentCacheRow> findCachedByDepartmentName(String departmentName) {
        if (departmentName == null || departmentName.isBlank()) {
            return Optional.empty();
        }
        return departmentCache.get(departmentName).stream().findFirst();
    }

    public List<DepartmentCacheRow> findAll() {
        return departmentCache.get(CacheConfig.TOTAL_KEY);
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
            departmentRepository.saveAndFlush(department);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 부서명입니다.");
        }

        cacheInvalidator.afterDepartmentChange(Set.of(name), false);
        return department.getDepartmentId();
    }

    @Transactional
    public void renameDepartment(Long departmentId, String departmentName) {
        Department department = departmentRepository.findByIdForUpdate(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "부서 정보를 찾을 수 없습니다."));

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
    public void deleteDepartment(Long departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "부서 정보를 찾을 수 없습니다."));
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
