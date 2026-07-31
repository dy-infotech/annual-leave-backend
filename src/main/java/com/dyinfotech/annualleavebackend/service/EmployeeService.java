package com.dyinfotech.annualleavebackend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
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

import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Employee;
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
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeLeaveService employeeLeaveService;
    
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

        Role role = employeeLeaveService.createSingleRoleResolver(employeeId).resolveRole();
        Float remainingDays = commonService.getRemainingDays(employee);

        return EmployeeDto.EmployeeResponse.from(employee, approver, role, remainingDays);
    }
    
    public List<EmployeeDto.EmployeeResponse> getAllEmployees(String searchParam) {
    	List<Employee> employees;
    	MultipleEmployeeRoleResolver roleResolver;
//    	// XXX: 주석 처리된 부분은 remainingLeaveDays가 필요할 경우에만 사용. 현재는 필요하지 않다고 판단함.
//    	Map<Long, Float> remainingLeaveDaysMap;
    	if (searchParam == null || searchParam.isBlank()) {
    		employees = employeeRepository.findAllEmployees();
    		roleResolver = employeeLeaveService.createRoleResolver();
//    		Collection<Long> employeeIds = employees.stream().map(Employee::getEmployeeId).toList();
//    		remainingLeaveDaysMap = employeeLeaveService.getAdjustedLeaveDays(employeeIds, employees.get(0).getCurrYear());
    	} else {
    		employees = employeeRepository.findAllEmployees(searchParam);
    		Collection<Long> employeeIds = employees.stream().map(Employee::getEmployeeId).toList();
    		roleResolver = employeeLeaveService.createRoleResolver(employeeIds);
//    		remainingLeaveDaysMap = employeeLeaveService.getAdjustedLeaveDays(employeeIds, employees.get(0).getCurrYear());
    	}
    	
    	List<EmployeeResponse> responses = new ArrayList<>();
        for (Employee employee : employees) {
            // XXX: approver 데이터 필요 없어서 뺐음.
//			responses.add(EmployeeResponse.from(employee, employee, roleResolver.resolveRole(employee.getEmployeeId()), remainingLeaveDaysMap.get(employee.getEmployeeId())));
            responses.add(EmployeeResponse.from(employee, employee, roleResolver.resolveRole(employee.getEmployeeId()), 0.0f));
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
}