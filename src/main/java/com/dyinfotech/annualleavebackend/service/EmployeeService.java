package com.dyinfotech.annualleavebackend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.cache.EmployeeCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.cache.OrganizationCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.type.PositionType;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.dto.EmployeeDto.EmployeeResponse;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.TeamManagerRepository;
import com.dyinfotech.annualleavebackend.repository.projection.EmployeeNumberEmail;
import com.dyinfotech.annualleavebackend.service.EmployeeLeaveService.EmployeeAuthorityResolver;
import com.dyinfotech.annualleavebackend.service.TeamService.ManagedTeam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

	private final TeamService teamService;
	private final DepartmentService departmentService;
	private final CommonService commonService;
    private final EmployeeLeaveService employeeLeaveService;
    private final EmployeeRepository employeeRepository;
    private final TeamManagerRepository teamManagerRepository;
    private final OrganizationCacheInvalidator cacheInvalidator;
    private final EmployeeCacheInvalidator employeeCacheInvalidator;
    private final PasswordEncoder passwordEncoder;
    
    @Cacheable(value = CacheConfig.CACHE_EMPLOYEES, key = "@employeeViewCacheKey.key(#a0)")
    public EmployeeDto.EmployeeResponse getMyInfo(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> {
                	String errorMsg = "존재하지 않는 직원입니다.";
                	log.error(errorMsg + " " + "employeeId: " + employeeId);
                	return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
                });

        Employee approver = teamService.resolveCurrentApprover(employee);
        float currTotalLeaveDays = employeeLeaveService.getCalculatedCurrYearLeaveDays(employee);
        Float remainingDays = commonService.getRemainingDaysByCurrTotalLeaveDays(employee, currTotalLeaveDays);

        return EmployeeDto.EmployeeResponse.from(employee, approver, employeeLeaveService.createAuthorityResolver(employeeId), currTotalLeaveDays, remainingDays);
    }
    
    public List<EmployeeDto.EmployeeResponse> getAllEmployees(String searchParam) {
    	List<Employee> employees;
    	EmployeeAuthorityResolver roleResolver;
    	// XXX: 주석 처리된 부분은 remainingLeaveDays가 필요할 경우에만 사용. 현재는 필요하지 않다고 판단함.
    	if (searchParam == null || searchParam.isBlank()) {
    		employees = employeeRepository.findAllEmployees();
    		roleResolver = employeeLeaveService.createAuthorityResolver();
    	} else {
    		employees = employeeRepository.findAllEmployees(searchParam);
    		roleResolver = employeeLeaveService.createAuthorityResolver(employees.stream()
    																			.map(Employee::getEmployeeId)
    																			.collect(Collectors.toSet()));
    	}
    	Map<Long, Float> remainingLeaveDaysMap = commonService.getRemainingDays(employees);
    	
    	List<EmployeeResponse> responses = new ArrayList<>();
        for (Employee employee : employees) {
            // XXX: approver 데이터 필요 없어서 뺐음.
			float currTotalLeaveDays = employeeLeaveService.getCalculatedCurrYearLeaveDays(employee);
			responses.add(EmployeeResponse.from(employee, employee, roleResolver, currTotalLeaveDays, remainingLeaveDaysMap.get(employee.getEmployeeId())));
        }

        return responses;
    }

    @Transactional
    public void changeEmail(Long employeeId, String email) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> {
                	String errorMsg = "존재하지 않는 직원입니다.";
                	log.error(errorMsg + " " + "employeeId: " + employeeId);
                	return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
                });
        employee.changeEmail(email);
        employeeCacheInvalidator.afterEmployeeChange(
                employeeId,
                List.of(employee.getName()),
                employee.getEmployeeNumber());
    }

    @Transactional
    public void changePassword(Long employeeId, EmployeeDto.PasswordChangeRequest request) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> {
                	String errorMsg = "존재하지 않는 직원입니다.";
                	log.error(errorMsg + " " + "employeeId: " + employeeId);
                	return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
                });
        

        // 비밀번호 일치 여부 확인
        if (!passwordEncoder.matches(request.getCurrentPassword(), employee.getPassword())) {
        	log.error("비밀번호 에러 employeeId : " + employee.getEmployeeId() + ",failCount : " + employee.getAccessCount());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }

        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        employee.changePassword(encodedNewPassword);
    }
	// 로그인 실패시 접근 횟수 추가
    @Transactional
    public void increaseAccessCount(Long employeeId, LocalDateTime now) {
		employeeRepository.increaseAccessCount(employeeId, now);
	}
	// 로그인 성공시 또는 접근 차단 시간 초과시 접근 횟수 초기화
    @Transactional
    public void resetAccessCount(Long employeeId, LocalDateTime now) {
    	employeeRepository.resetAccessCount(employeeId, now);
    }
    // 로그인 성공시 평문 패스워드 암호화
    @Transactional
    public void updatePassword(Long employeeId, String password) {
        employeeRepository.updatePassword(employeeId, password);
    }
    // 로그인 성공시 올해 총 연차 수 업데이트
    @Transactional
    public void updateCurrTotalLeaveDays(Long employeeId, float days) {
        employeeRepository.updateCurrTotalLeaveDays(employeeId, days);
        employeeCacheInvalidator.afterEmployeeViewChange(employeeId);
    }
    
    public Optional<Employee> findByPrefixEmployeeNumber(String prefix) {	// 신규 사번 등록 실패도 있으므로 캐싱 미처리
    	return employeeRepository.findFirstByEmployeeNumberStartingWithOrderByEmployeeNumberDesc(prefix);
    }
    
    public List<Employee> getEmployeeList(Collection<Long> employeeIds) {	// 요청자와 관리자의 쌍이 캐시 히트 효율이 낮으므로 캐싱 미처리
    	return employeeRepository.findAllByEmployeeIdInOrderByEmployeeIdAsc(employeeIds);
    }
    
    public Optional<Employee> getEmployee(String employeeNumber) {			// signIn, signUp에 쓰이는 데이터라서 쓰기 작업으로 오염될 것이므로 캐싱 미처리
    	return employeeRepository.findByEmployeeNumber(employeeNumber);
    }
    
    // 계정 찾기 
    
    public List<EmployeeNumberEmail> findEmployeeNumberAndEmailByNameAndEmailIn(String name, List<String> emailList) {
        return employeeRepository.findEmployeeNumberAndEmailByNameAndEmailIn(name, emailList);
    }
    
    public Optional<Employee> getEmployee(String employeeNumber, String email) {	// forgotPassword에 쓰이는 데이터라서 쓰기 작업으로 오염될 것이므로 캐싱 미처리
    	return employeeRepository.findByEmployeeNumberAndEmail(employeeNumber, email);
    }
    
    public List<String> findEmailsByName(String name) {
    	return employeeRepository.findEmailsByName(name);
    }
    
    public String findEmailsByEmployeeNumber(String employeeNumber) {
    	return employeeRepository.findEmailsByEmployeeNumber(employeeNumber);
    }
    
    
    
    
    @Transactional
    public void saveEmployee(Employee employee) {
    	employeeRepository.save(employee);
    	employeeCacheInvalidator.afterEmployeeChange(
    			employee.getEmployeeId(),
    			List.of(employee.getName()),
    			employee.getEmployeeNumber());
    }
    
    // 사원 정보가 수정되면 커밋 후 관련 캐시만 무효화하여 데이터 정합성을 유지합니다.
    @Transactional
    public void updateEmployeeByAdmin(Long approverId, String employeeNumber, EmployeeDto.EmployeeAdminUpdateRequest request) {
        Employee approver = employeeRepository.findById(approverId)
                .orElseThrow(() -> {
                    String errorMsg = "존재하지 않는 관리자입니다.";
                    log.error(errorMsg + " employeeId: " + approverId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
                });

        if (!approver.hasPersonnelAuthority()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "인사권을 가진 관리자가 아닙니다.");
        }

        Employee employee = employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() -> {
                    String errorMsg = "존재하지 않는 직원입니다.";
                    log.error(errorMsg + " employeeNumber: " + employeeNumber);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
                });

        Long employeeId = employee.getEmployeeId();
        String oldEmployeeName = employee.getName();
        List<Long> managedTeamIdsBeforeUpdate = teamManagerRepository.findTeamIdsByProjectManagerId(employeeId).stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        Department requestedDepartment = departmentService.findByDepartmentName(request.getDepartment())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "부서 정보가 잘못되었습니다."));

        Collection<String> targetTeams = request.getTargetTeamsForRoleSwap();
        if (targetTeams == null || targetTeams.isEmpty()) {
            targetTeams = Collections.emptyList();
        }

        Set<Long> plannedTeamIds = new HashSet<>(managedTeamIdsBeforeUpdate);
        if (employee.getTeamId() != null) {
            plannedTeamIds.add(employee.getTeamId());
        }

        Map<String, Long> requestedTeamIds = new HashMap<>();
        Map<String, Long> requestedParentTeamIds = new HashMap<>();
        for (String targetTeam : targetTeams) {
            var teamInfo = teamService.findTeamInfo(targetTeam)
                    .orElseThrow(() -> {
                        String errorMsg = "존재하지 않는 관리 팀으로 수정 요청했습니다. requestedTeam : " + targetTeam;
                        log.error(errorMsg + " employeeNumber: " + employeeNumber);
                        return new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg);
                    });
            Long parentTeamId = teamService.resolveParentTeamId(targetTeam)
                    .orElse(approver.getTeamId());
            requestedTeamIds.put(targetTeam, teamInfo.teamId());
            requestedParentTeamIds.put(targetTeam, parentTeamId);
            plannedTeamIds.add(teamInfo.teamId());
            plannedTeamIds.add(parentTeamId);
        }

        Long requestedEmployeeTeamId = null;
        if (request.getTeam() != null && !request.getTeam().trim().isEmpty()) {
            var teamInfo = teamService.findTeamInfo(request.getTeam())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "팀 정보가 잘못되었습니다."));
            requestedEmployeeTeamId = teamInfo.teamId();
            plannedTeamIds.add(requestedEmployeeTeamId);
        }

        // TEAM 잠금을 전체 집합에 대해 ID 오름차순으로 먼저 획득한 뒤 Employee 잠금을 잡는다.
        teamService.lockTeamsForUpdate(plannedTeamIds);
        employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        List<Long> currentManagedTeamIds = teamManagerRepository.findTeamIdsByProjectManagerId(employeeId).stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (!plannedTeamIds.containsAll(currentManagedTeamIds)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "담당 팀 정보가 동시에 변경되었습니다. 다시 시도해주세요.");
        }

        Map<String, Long> managedByEmployee = new HashMap<>();
        for (Long managedTeamId : currentManagedTeamIds) {
            teamService.findTeamInfo(managedTeamId)
                    .ifPresent(teamInfo -> managedByEmployee.put(teamInfo.teamName(), teamInfo.teamId()));
        }

        for (String targetTeam : targetTeams) {
            Long managedTeamId = managedByEmployee.get(targetTeam);
            if (managedTeamId != null) {
                teamService.removeManager(managedTeamId, employeeId);
            } else {
                Long targetTeamId = requestedTeamIds.get(targetTeam);
                Long parentTeamId = requestedParentTeamIds.get(targetTeam);
                String targetTeamName = teamService.findTeamInfo(targetTeamId)
                        .map(info -> info.teamName())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "팀 정보가 동시에 변경되었습니다."));
                teamService.addManager(targetTeamName, employeeId, parentTeamId);
            }
        }

        Team team = employee.getTeam();
        if (requestedEmployeeTeamId != null) {
            team = teamService.findByTeamName(request.getTeam())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "팀 정보가 잘못되었습니다."));
            if (!team.getTeamId().equals(requestedEmployeeTeamId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "팀 정보가 동시에 변경되었습니다. 다시 시도해주세요.");
            }
        }

        String finalPosition = request.getPosition() != null && !request.getPosition().trim().isEmpty()
                ? request.getPosition()
                : employee.getPosition();

        if (PositionType.getType(finalPosition) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "직급 정보가 잘못되었습니다.");
        }
        if (request.getFireDate() != null && request.getFireDate().isBefore(request.getHireDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "퇴사일은 입사일 이후여야 합니다.");
        }

        teamService.validateManagerDeactivation(employeeId, request.getFireDate());
        teamService.requireActiveManager(team.getTeamId());

        Department department = team.getDepartment();
        if (!department.getDepartmentId().equals(requestedDepartment.getDepartmentId())) {
            log.warn("요청 부서와 팀의 소속 부서가 달라 팀의 부서로 저장합니다. requested: {}, teamDepartment: {}",
                    requestedDepartment.getDepartmentName(), department.getDepartmentName());
        }

        employee.updateInfoByAdmin(
                request.getName() != null ? request.getName() : employee.getName(),
                request.getEmail() != null ? request.getEmail() : employee.getEmail(),
                department,
                team,
                finalPosition,
                request.getHireDate(),
                request.getFireDate(),
                employeeLeaveService.getCalculatedCurrYearLeaveDays(request.getHireDate())
        );

        Set<Long> coverageTeamIds = new HashSet<>();
        coverageTeamIds.add(team.getTeamId());
        coverageTeamIds.addAll(teamManagerRepository.findTeamIdsByProjectManagerId(employeeId));
        teamService.validateFutureApprovalCoverage(coverageTeamIds);

        cacheInvalidator.afterEmployeeOrganizationChange(managedTeamIdsBeforeUpdate);
        employeeCacheInvalidator.afterEmailLookupChange(
                List.of(oldEmployeeName, employee.getName()),
                employee.getEmployeeNumber());
    }
}