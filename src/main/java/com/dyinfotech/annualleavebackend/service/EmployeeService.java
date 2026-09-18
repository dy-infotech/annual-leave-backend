package com.dyinfotech.annualleavebackend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.DepartmentType;
import com.dyinfotech.annualleavebackend.common.type.PositionType;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.dto.EmployeeDto.EmployeeResponse;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.projection.EmployeeNumberEmail;
import com.dyinfotech.annualleavebackend.service.EmployeeLeaveService.EmployeeAuthorityResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

	private final TeamService teamService;
	private final CommonService commonService;
    private final EmployeeLeaveService employeeLeaveService;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Cacheable(value = CacheConfig.CACHE_EMPLOYEES, key = "#a0")
    public EmployeeDto.EmployeeResponse getMyInfo(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> {
                	String errorMsg = "존재하지 않는 직원입니다.";
                	log.error(errorMsg + " " + "employeeId: " + employeeId);
                	return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
                });

        Employee approver = employeeRepository.findById(employee.getApproverId())
                .orElseThrow(() -> {
                    String errorMsg = "존재하지 않는 직원(결재자)입니다.";
                    log.error(errorMsg + " " + "employeeId: " + employeeId + ", approverId: " + employee.getApproverId());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
                });
        Float remainingDays = commonService.getRemainingDays(employee);

        return EmployeeDto.EmployeeResponse.from(employee, approver, employeeLeaveService.createAuthorityResolver(employeeId), remainingDays);
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
			responses.add(EmployeeResponse.from(employee, employee, roleResolver, remainingLeaveDaysMap.get(employee.getEmployeeId())));
        }

        return responses;
    }

    @Transactional
    @Caching(evict = {
    	    // 1. 해당 직원의 단건 캐시(getMyInfo) 날리기
    	    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, key = "#a0"),
    	    // 2. 재직 중인 직원 전체 목록 캐시('active')도 같이 날리기
    	    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, key = "'active'")
    	})
    public void changeEmail(Long employeeId, String email) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> {
                	String errorMsg = "존재하지 않는 직원입니다.";
                	log.error(errorMsg + " " + "employeeId: " + employeeId);
                	return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
                });
        employee.changeEmail(email);
    }

    @Transactional
    @Caching(evict = {
    	    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, key = "#a0"),
    	    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, key = "'active'")
    	})
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
    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, key = "#a0")
    public void updateCurrTotalLeaveDays(Long employeeId, float days) {
        employeeRepository.updateCurrTotalLeaveDays(employeeId, days);
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
    @Caching(evict = {
    	    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, allEntries = true),
    	    @CacheEvict(value = CacheConfig.CACHE_TEAM_MANAGEMENT_DATA, allEntries = true)
    })
    public void saveEmployee(Employee employee) {
    	employeeRepository.save(employee);
    }
    
    // 사원 정보가 수정되면 캐시를 전체 초기화하여 데이터 정합성을 유지합니다.
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_TEAM_MANAGEMENT_DATA, allEntries = true)
    })
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

        Collection<String> targetTeams = request.getTargetTeamsForRoleSwap();
        if (targetTeams == null || targetTeams.isEmpty()) {
            targetTeams = Collections.emptyList();
        } else {
            if (!approver.hasPersonnelAuthority()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "팀 내부 역할 변경은 " + PositionType.CEO.getName() + "만 할 수 있습니다."
                );
            }
        }

        for (String targetTeam : targetTeams) {
            Team teamEntity = null;
            for (Team team : employee.getTeams()) {
                if (team.getTeam().equals(targetTeam)) {
                    teamEntity = team;
                    break;
                }
            }

            if (teamEntity != null) {
                // 관리자 -> 멤버
                teamService.deleteTeam(teamEntity);
            } else {
                // 멤버 -> 관리자
                approver.getTeams()
                        .stream()
                        .flatMap(e -> teamService.getSelfAndDescendants(e.getTeam()).stream())
                        .filter(e -> e.getTeam().equals(targetTeam))
                        .findAny()
                        .ifPresentOrElse(
                                team -> teamService.saveTeam(new Team(team.getTeam(), employee, team.getParentTeam())),
                                () -> {
                                    String errorMsg = "존재하지 않는 관리 팀으로 수정 요청했습니다. requestedTeam : " + targetTeam;
                                    log.error(errorMsg + " employeeNumber: " + employeeNumber);
                                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg);
                                }
                        );
            }
        }

        String finalTeam = request.getTeam() != null && !request.getTeam().trim().isEmpty()
                ? request.getTeam()
                : employee.getTeam();
        String finalPosition = request.getPosition() != null && !request.getPosition().trim().isEmpty()
                ? request.getPosition()
                : employee.getPosition();

        if (DepartmentType.getType(request.getDepartment()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "부서 정보가 잘못되었습니다.");
        }
        if (PositionType.getType(finalPosition) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "직급 정보가 잘못되었습니다.");
        }
        if (teamService.findAllByTeam(finalTeam).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "팀 정보가 잘못되었습니다.");
        }
        if (request.getFireDate() != null && request.getFireDate().isBefore(request.getHireDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "퇴사일은 입사일 이후여야 합니다.");
        }

        employee.updateInfoByAdmin(
                request.getName() != null ? request.getName() : employee.getName(),
                request.getEmail() != null ? request.getEmail() : employee.getEmail(),
                request.getDepartment() != null ? request.getDepartment() : employee.getDepartment(),
                finalTeam,
                finalPosition,
                request.getHireDate(),
                request.getFireDate(),
                employeeLeaveService.getCalculatedCurrYearLeaveDays(request.getHireDate())
        );
    }

}