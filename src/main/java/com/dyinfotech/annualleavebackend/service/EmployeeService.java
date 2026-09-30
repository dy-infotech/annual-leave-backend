package com.dyinfotech.annualleavebackend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
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
    
    @Transactional
    public void updateManagedTeamsByAdmin(
            Long approverId,
            String employeeNumber,
            EmployeeDto.ManagedTeamsUpdateRequest request) {
        Employee approver = employeeRepository.findById(approverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 관리자입니다."));
        if (!approver.hasPersonnelAuthority()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "인사권을 가진 관리자가 아닙니다.");
        }

        Employee employee = employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));
        Long employeeId = employee.getEmployeeId();

        Set<String> expectedManagedTeams = normalizeManagedTeamNames(request.getExpectedManagedTeams());
        Set<String> desiredManagedTeams = normalizeManagedTeamNames(request.getManagedTeams());

        List<Long> managedTeamIdsBeforeUpdate = teamManagerRepository.findTeamIdsByProjectManagerId(employeeId).stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        Set<Long> plannedTeamIds = new HashSet<>(managedTeamIdsBeforeUpdate);
        Map<String, Long> desiredTeamIds = new HashMap<>();
        Map<String, Long> desiredParentTeamIds = new HashMap<>();

        for (String teamName : desiredManagedTeams) {
            var teamInfo = teamService.findTeamInfo(teamName)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "존재하지 않는 관리 팀입니다. requestedTeam: " + teamName));
            Long parentTeamId = teamService.resolveParentTeamId(teamName)
                    .orElse(approver.getTeamId());
            desiredTeamIds.put(teamName, teamInfo.teamId());
            desiredParentTeamIds.put(teamName, parentTeamId);
            plannedTeamIds.add(teamInfo.teamId());
            if (parentTeamId != null) {
                plannedTeamIds.add(parentTeamId);
            }
        }

        for (String teamName : expectedManagedTeams) {
            var teamInfo = teamService.findTeamInfo(teamName)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "관리 팀 정보가 변경되었습니다. 다시 조회해주세요."));
            plannedTeamIds.add(teamInfo.teamId());
        }

        // 모든 관련 TEAM을 ID 오름차순으로 잠근 뒤 Employee를 잠가 동일 직원의 관리팀 변경을 직렬화한다.
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
                    "담당 팀 정보가 동시에 변경되었습니다. 다시 조회해주세요.");
        }

        Map<String, Long> currentManagedByName = new HashMap<>();
        for (Long managedTeamId : currentManagedTeamIds) {
            teamService.findTeamInfo(managedTeamId)
                    .ifPresent(teamInfo -> currentManagedByName.put(teamInfo.teamName(), teamInfo.teamId()));
        }
        Set<String> currentManagedTeams = new java.util.LinkedHashSet<>(currentManagedByName.keySet());

        // 동일 요청 재전송: 첫 요청이 이미 반영됐다면 expected가 과거 상태여도 성공 no-op으로 처리한다.
        if (currentManagedTeams.equals(desiredManagedTeams)) {
            Long oldApproverId = employee.getApproverId();
            teamService.refreshApproverIds(employee);
            if (!Objects.equals(oldApproverId, employee.getApproverId())) {
                employeeCacheInvalidator.afterEmployeeViewChange(employeeId);
            }
            return;
        }

        // 서로 다른 관리자가 같은 과거 화면에서 수정한 경우 뒤늦은 저장으로 덮어쓰지 않는다.
        if (!currentManagedTeams.equals(expectedManagedTeams)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "관리 팀 정보가 다른 요청에 의해 변경되었습니다. 다시 조회해주세요.");
        }

        Set<String> teamsToRemove = new java.util.LinkedHashSet<>(currentManagedTeams);
        teamsToRemove.removeAll(desiredManagedTeams);

        Set<String> teamsToAdd = new java.util.LinkedHashSet<>(desiredManagedTeams);
        teamsToAdd.removeAll(currentManagedTeams);

        Set<Long> removedManagerTeamIds = new HashSet<>();
        Map<Long, Long> addedManagerParentTeamIds = new HashMap<>();

        for (String teamName : teamsToRemove) {
            Long teamId = currentManagedByName.get(teamName);
            if (teamId != null) {
                removedManagerTeamIds.add(teamId);
            }
            teamService.removeManager(teamId, employeeId);
        }
        for (String teamName : teamsToAdd) {
            Long teamId = desiredTeamIds.get(teamName);
            Long parentTeamId = desiredParentTeamIds.get(teamName);
            addedManagerParentTeamIds.put(teamId, parentTeamId);
            teamService.addManager(teamName, employeeId, parentTeamId);
        }

        Long oldApproverId = employee.getApproverId();
        teamService.refreshApproverIds(employee, removedManagerTeamIds, addedManagerParentTeamIds);
        if (!Objects.equals(oldApproverId, employee.getApproverId())) {
            employeeCacheInvalidator.afterEmployeeViewChange(employeeId);
        }
    }

    private Set<String> normalizeManagedTeamNames(Collection<String> teamNames) {
        if (teamNames == null || teamNames.isEmpty()) {
            return Collections.emptySet();
        }
        return teamNames.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(teamName -> !teamName.isEmpty())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
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
        List<Long> managedTeamIdsBeforeUpdate = teamManagerRepository.findTeamIdsByProjectManagerId(employeeId).stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        Department requestedDepartment = departmentService.findByDepartmentName(request.getDepartment())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "부서 정보가 잘못되었습니다."));

        // 관리팀 추가/삭제는 /managed-teams 전용 CAS 엔드포인트에서만 처리한다.
        // full employee PUT은 기존 관리팀을 잠가 인사정보 변경과의 동시성만 보장한다.
        Set<Long> plannedTeamIds = new HashSet<>(managedTeamIdsBeforeUpdate);
        if (employee.getTeamId() != null) {
            plannedTeamIds.add(employee.getTeamId());
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

        String oldEmployeeName = employee.getName();
        String oldManagerPosition = employee.getPosition();
        LocalDate oldManagerHireDate = employee.getHireDate();
        LocalDate oldManagerFireDate = employee.getFireDate();

        if (request.getExpected() != null) {
            if (employeeMatchesDesiredState(employee, request)) {
                // 동일 full PUT 재전송이어도 legacy stale approver_id는 현재 조직 기준으로 self-heal한다.
                Long oldApproverId = employee.getApproverId();
                teamService.refreshApproverIds(employee);
                if (!Objects.equals(oldApproverId, employee.getApproverId())) {
                    employeeCacheInvalidator.afterEmployeeViewChange(employeeId);
                }
                return;
            }

            if (!employeeMatchesExpectedState(employee, request.getExpected())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "사원 정보가 다른 요청에 의해 변경되었습니다. 다시 조회해주세요.");
            }
        }

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

        Team team = employee.getTeam();
        if (requestedEmployeeTeamId != null) {
            team = teamService.findByTeamName(request.getTeam())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "팀 정보가 잘못되었습니다."));
            if (!team.getTeamId().equals(requestedEmployeeTeamId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "팀 정보가 동시에 변경되었습니다. 다시 시도해주세요.");
            }
        }

        String finalName = request.getName() != null ? request.getName() : employee.getName();
        String finalEmail = request.getEmail() != null ? request.getEmail() : employee.getEmail();
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

        boolean managerSnapshotChanged =
                !Objects.equals(oldEmployeeName, finalName)
                        || !Objects.equals(oldManagerPosition, finalPosition)
                        || !Objects.equals(oldManagerHireDate, request.getHireDate())
                        || !Objects.equals(oldManagerFireDate, request.getFireDate());

        employee.updateInfoByAdmin(
                finalName,
                finalEmail,
                department,
                team,
                finalPosition,
                request.getHireDate(),
                request.getFireDate(),
                employeeLeaveService.getCalculatedCurrYearLeaveDays(request.getHireDate())
        );

        // TeamManager 자체는 이 API에서 변경하지 않으므로 현재 조직 snapshot 기준으로 approver_id만 교정한다.
        teamService.refreshApproverIds(employee);

        Set<Long> coverageTeamIds = new HashSet<>();
        coverageTeamIds.add(team.getTeamId());
        coverageTeamIds.addAll(teamManagerRepository.findTeamIdsByProjectManagerId(employeeId));
        teamService.validateFutureApprovalCoverage(coverageTeamIds);

        if (managerSnapshotChanged && !currentManagedTeamIds.isEmpty()) {
            // TeamManagerCacheRow에 실제 포함되는 PM 필드가 바뀐 팀만 조직 snapshot을 무효화한다.
            cacheInvalidator.afterEmployeeOrganizationChange(currentManagedTeamIds);
        }
        // 단순 CCC -> ABC 이동은 전 조직 generation 대신 해당 직원 view만 새 세대로 보낸다.
        employeeCacheInvalidator.afterEmployeeViewChange(employeeId);
        employeeCacheInvalidator.afterEmailLookupChange(
                List.of(oldEmployeeName, employee.getName()),
                employee.getEmployeeNumber());
    }

    private boolean employeeMatchesExpectedState(
            Employee employee,
            EmployeeDto.EmployeeAdminExpectedState expected) {
        return Objects.equals(employee.getName(), expected.getName())
                && Objects.equals(employee.getEmail(), expected.getEmail())
                && Objects.equals(employee.getDepartmentName(), expected.getDepartment())
                && Objects.equals(employee.getTeamName(), expected.getTeam())
                && Objects.equals(employee.getPosition(), expected.getPosition())
                && Objects.equals(employee.getHireDate(), expected.getHireDate())
                && Objects.equals(employee.getFireDate(), expected.getFireDate());
    }

    private boolean employeeMatchesDesiredState(
            Employee employee,
            EmployeeDto.EmployeeAdminUpdateRequest request) {
        return Objects.equals(employee.getName(), request.getName())
                && Objects.equals(employee.getEmail(), request.getEmail())
                && Objects.equals(employee.getDepartmentName(), request.getDepartment())
                && Objects.equals(employee.getTeamName(), request.getTeam())
                && Objects.equals(employee.getPosition(), request.getPosition())
                && Objects.equals(employee.getHireDate(), request.getHireDate())
                && Objects.equals(employee.getFireDate(), request.getFireDate());
    }

}