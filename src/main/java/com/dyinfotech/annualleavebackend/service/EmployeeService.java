package com.dyinfotech.annualleavebackend.service;

import com.dyinfotech.annualleavebackend.repository.TeamRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.PositionType;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.dto.EmployeeDto.EmployeeResponse;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.projection.EmployeeNumberEmail;
import com.dyinfotech.annualleavebackend.service.EmployeeLeaveService.MultipleEmployeeRoleResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

	private final CommonService commonService;
    private final EmployeeLeaveService employeeLeaveService;
    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Cacheable(value = CacheConfig.CACHE_EMPLOYEES, key = "#a0")
    public EmployeeDto.EmployeeResponse getMyInfo(Long employeeId, Role role) {
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

        return EmployeeDto.EmployeeResponse.from(employee, approver, role, remainingDays);
    }
    
    public List<EmployeeDto.EmployeeResponse> getAllEmployees(String searchParam) {
    	List<Employee> employees;
    	MultipleEmployeeRoleResolver roleResolver;
    	// XXX: 주석 처리된 부분은 remainingLeaveDays가 필요할 경우에만 사용. 현재는 필요하지 않다고 판단함.
    	if (searchParam == null || searchParam.isBlank()) {
    		employees = employeeRepository.findAllEmployees();
    		roleResolver = employeeLeaveService.createRoleResolver();
    	} else {
    		employees = employeeRepository.findAllEmployees(searchParam);
    		Collection<Long> employeeIds = employees.stream().map(Employee::getEmployeeId).toList();
    		roleResolver = employeeLeaveService.createRoleResolver(employeeIds);
    	}
    	Map<Long, Float> remainingLeaveDaysMap = commonService.getRemainingDays(employees);
    	
    	List<EmployeeResponse> responses = new ArrayList<>();
        for (Employee employee : employees) {
            // XXX: approver 데이터 필요 없어서 뺐음.
			responses.add(EmployeeResponse.from(employee, employee, roleResolver.resolveRole(employee.getEmployeeId()), remainingLeaveDaysMap.get(employee.getEmployeeId())));
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseAccessCount(Long employeeId, LocalDateTime now) {
		employeeRepository.increaseAccessCount(employeeId, now);
	}
	// 로그인 성공시 또는 접근 차단 시간 초과시 접근 횟수 초기화
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetAccessCount(Long employeeId, LocalDateTime now) {
    	employeeRepository.resetAccessCount(employeeId, now);
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
    
    public List<String> findEmailsByEmployeeNumber(String employeeNumber) {
    	return employeeRepository.findEmailsByEmployeeNumber(employeeNumber);
    }
    
    
    
    
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, allEntries = true)
    public void saveEmployee(Employee employee) {
    	employeeRepository.save(employee);
    }
    
    
    @Transactional
    // 사원 정보가 수정되면 캐시를 전체 초기화하여 데이터 정합성을 유지합니다.
    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, allEntries = true)
    public void updateEmployeeByAdmin(String employeeNumber, EmployeeDto.EmployeeAdminUpdateRequest request) {
        // 사번으로 기존 직원 엔티티 조회
        Employee employee = employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() -> {
                    String errorMsg = "존재하지 않는 직원입니다.";
                    log.error(errorMsg + " employeeNumber: " + employeeNumber);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
                });
    	
        // 관리 팀 변경 요청시 처리
    	List<String> targetTeams = request.getTargetTeamsForRoleSwap();
    	if (targetTeams == null || targetTeams.isEmpty()) {
    		targetTeams = Collections.emptyList();
    	} else {
    		// 사장 이외 요청 거부 (사원 등록 시 조건과 일치)
    		if (!employee.canMakeAdmin()) {
    			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "팀 내부 역할 변경은 " + PositionType.CEO.getName() + "만 할 수 있습니다.");
    		}
    	}
    	for (String targetTeam : targetTeams) {
    		// 해당 팀명으로 관리중인 팀이 존재한다면 탐색
    		Team teamEntity = null;
    		for (Team team : employee.getTeams()) {
    			if (team.getTeam().equals(targetTeam)) {
    				teamEntity = team;
    				break;
    			}
    		}
    		
    		if (teamEntity != null) {
    			// 관리자 -> 멤버
    			teamRepository.delete(teamEntity);
    		} else {
    			// 멤버 -> 관리자
    			Team team = teamRepository.findFirstByTeamOrderBySeqAsc(targetTeam);
    			if (team != null) {
    				teamRepository.save(new Team(team.getTeam(), employee, team.getParentTeam()));
    			} else {
    				String errorMsg = "존재하지 않는 관리 팀으로 수정 요청했습니다. requestedTeam : " + targetTeam;
    				log.error(errorMsg + " employeeNumber: " + employeeNumber);
    				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg);
    			}
    		}
    	}

        // [입사일 가공 처리] 엔티티의 LocalDate 규격에 맞게 파싱 진행 (String 수용)
        java.time.LocalDate parsedHireDate = null;
        if (request.getHireDate() != null) {
            String hireDateStr = String.valueOf(request.getHireDate()).trim();
            if (!hireDateStr.isEmpty() && !hireDateStr.equals("null")) {
                // yyyy-MM-dd 형태의 앞 10자리만 안전하게 잘라내어 파싱합니다.
                parsedHireDate = java.time.LocalDate.parse(hireDateStr.substring(0, 10));
            }
        }
        // 만약 파싱에 실패했다면 기존 엔티티가 가지고 있던 원래 입사일을 유지합니다.
        if (parsedHireDate == null) {
            parsedHireDate = employee.getHireDate();
        }

        // [팀 정보 누락 방어] 프론트 첫 번째 PUT API 구조상 team이 누락되므로 
        // request.getTeam()이 비어 있다면 기존 엔티티의 team 정보를 그대로 보존합니다.
        String finalTeam = (request.getTeam() != null && !request.getTeam().trim().isEmpty()) 
                ? request.getTeam() 
                : employee.getTeam();

    	LocalDate hireDate = LocalDate.parse(request.getHireDate());
    	
        // [엔티티 메서드 호출] 가공 및 유실 방어가 완료된 필드들을 인자에 차례대로 주입합니다.
        employee.updateInfoByAdmin(
            request.getName() != null ? request.getName() : employee.getName(),
            request.getEmail() != null ? request.getEmail() : employee.getEmail(),
            request.getDepartment() != null ? request.getDepartment() : employee.getDepartment(),
            finalTeam,                 // 👈 덮어쓰기가 방지된 안전한 팀 값 전달
            request.getPosition() != null ? request.getPosition() : employee.getPosition(),
            parsedHireDate,            // 👈 포맷 오류가 해결된 LocalDate 객체 주입
           // employee.getApproverId(),   // 👈 필수값인 결재자(approver_id) 원본 데이터 보존
           // request.getCurrTotalLeaveDays() != null ? request.getCurrTotalLeaveDays() : employee.getCurrTotalLeaveDays()
            employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate) 
            
        );
    }
//    @Transactional
//    @CacheEvict(value = CacheConfig.CACHE_EMPLOYEES, allEntries = true)
//    public void updateEmployeeByAdmin(String employeeNumber, EmployeeDto.EmployeeAdminUpdateRequest request) {
//        // 1. 사번으로 수정 대상 사원 조회
//        Employee employee = employeeRepository.findByEmployeeNumber(employeeNumber)
//                .orElseThrow(() -> {
//                    String errorMsg = "존재하지 않는 직원입니다.";
//                    log.error(errorMsg + " employeeNumber: " + employeeNumber);
//                    return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
//                });
//
//        // [팀 정보 누락 방어]
//        String finalTeam = (request.getTeam() != null && !request.getTeam().trim().isEmpty()) 
//                ? request.getTeam().trim() 
//                : employee.getTeam();
//
//        // 2. 💡 [권한 및 팀 동기화 마감] 프론트엔드에서 보낸 최신 권한 상태 변환 및 주입
//        if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
//            try {
//                Role targetRole = Role.valueOf(request.getRole().trim().toUpperCase());
//
//                // B. list 리솔버가 참조하는 team 테이블과의 연동 동기화 처리
//                if (targetRole == Role.ADMIN) {
//                    // [멤버 -> 관리자]: team 테이블에서 해당 팀을 찾아 이 직원을 project_manager_id로 매핑 강제 등록
//                    Team team = teamRepository.findFirstByTeamOrderBySeqAsc(finalTeam);
//                    if (team != null) {
//                        // 이미 등록되어 있지 않은 경우에만 중복 방지 저장
//                        boolean isAlreadyPm = employee.getTeams().stream().anyMatch(t -> t.getTeam().equals(finalTeam));
//                        if (!isAlreadyPm) {
//                            teamRepository.save(new Team(team.getTeam(), employee, team.getParentTeam()));
//                            log.info("▶ [JPA 연동] 사원 {}의 Role 리솔버 통과를 위해 team 테이블 PM 매핑 인서트 완료", employeeNumber);
//                        }
//                    }
//                } else if (targetRole == Role.EMPLOYEE) {
//                    // [관리자 -> 멤버]: 이 사원이 팀장으로 매핑된 레코드 관계를 team 테이블에서 강제 삭제
//                    for (Team team : new ArrayList<>(employee.getTeams())) {
//                        if (team.getTeam().equals(finalTeam)) {
//                            teamRepository.delete(team);
//                            log.info("▶ [JPA 연동] 사원 {}의 관리자 해제를 위해 team 테이블 PM 매핑 딜리트 완료", employeeNumber);
//                        }
//                    }
//                }
//            } catch (IllegalArgumentException e) {
//                log.warn("🚨 올바르지 않은 Role 규격이 전송되었습니다: " + request.getRole());
//            }
//        }
//
//        // 3. 입사일 가공 처리 로직 (기존 안정 버전 유지)
//        java.time.LocalDate parsedHireDate = null;
//        if (request.getHireDate() != null) {
//            String hireDateStr = String.valueOf(request.getHireDate()).trim();
//            if (!hireDateStr.isEmpty() && !hireDateStr.equals("null")) {
//                parsedHireDate = java.time.LocalDate.parse(hireDateStr.substring(0, 10));
//            }
//        }
//        if (parsedHireDate == null) {
//            parsedHireDate = employee.getHireDate();
//        }
//
//        java.time.LocalDate hireDate = parsedHireDate;
//    	
//        // 4. 엔티티 나머지 필드 일괄 업데이트 완료
//        employee.updateInfoByAdmin(
//            request.getName() != null ? request.getName() : employee.getName(),
//            request.getEmail() != null ? request.getEmail() : employee.getEmail(),
//            request.getDepartment() != null ? request.getDepartment() : employee.getDepartment(),
//            finalTeam,                 
//            request.getPosition() != null ? request.getPosition() : employee.getPosition(),
//            parsedHireDate,            
//            employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate) 
//        );
//    }

    
}