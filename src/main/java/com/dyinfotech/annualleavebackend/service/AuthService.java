package com.dyinfotech.annualleavebackend.service;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.common.jwt.JwtProvider;
import com.dyinfotech.annualleavebackend.common.type.BasisDataType;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.BasisData;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.dto.RegisterDto;
import com.dyinfotech.annualleavebackend.dto.SignInDto;
import com.dyinfotech.annualleavebackend.dto.SignUpDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final BasisDataFactory basisDataFactory;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final EmployeeLeaveService employeeLeaveService;
    
    @Transactional
    public RegisterDto.RegisterResponse registerEmployee(RegisterDto.RegisterRequest request) {
		// 사번 채번
    	LocalDate now = LocalDate.now();
    	Optional<BasisData> employeeNumberPrefix = basisDataFactory.get(BasisDataType.EMPlOYEE_NUMBER_PREFIX);
    	if (employeeNumberPrefix.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "사번 접두사 정보가 없습니다.");
		}

    	String currentYear = String.valueOf(now.getYear());
    	String prefix = employeeNumberPrefix.get().getData().replace("#{YEAR}", currentYear);
    	Optional<Employee> lastPrefixEmployee = employeeRepository.findFirstByEmployeeNumberStartingWithOrderByEmployeeNumberDesc(prefix);
    	
    	// 사번 설정
    	String formatString = "%03d";
    	String employeeNumber = null;
    	if (lastPrefixEmployee.isPresent()) {
			String lastEmployeeNumber = lastPrefixEmployee.get().getEmployeeNumber();
			int lastNumber = Integer.parseInt(lastEmployeeNumber.substring(prefix.length()));
			employeeNumber = prefix + String.format(formatString, lastNumber + 1);
		} else {
			employeeNumber = prefix + String.format(formatString, 1);
		}
    	
    	// 연차 할당
    	float currentYearFloat = 0.0f;
    	LocalDate hireDate = LocalDate.parse(request.getHireDate());
    	int yearsDifference = now.getYear() - hireDate.getYear();
    	if (yearsDifference > 0) {
    		// 1년차 할당 연차수 부여
    		yearsDifference -= 1;
    		currentYearFloat += basisDataFactory.getAsInteger(BasisDataType.FIRST_YEAR_LEAVE_DAYS).get();
    		
    		// N년당 할당 연차수 부여
    		int diff = basisDataFactory.getAsInteger(BasisDataType.N_YEARS_OF_ADDITIONAL_LEAVE).get();
    		int additionalLeaveDays = basisDataFactory.getAsInteger(BasisDataType.ADDITIONAL_LEAVE_DAYS).get();
    		for (; yearsDifference >= diff; yearsDifference -= diff) {
				currentYearFloat += additionalLeaveDays;
			}
    	} else {
    		// TODO: 입사년도가 현재년도와 일치할 경우 월차가 지급되므로 일단 처리하지 않는다.
    	}
    	
    	// 근로자 정보 등록
    	Employee employee = Employee.builder()
				.employeeNumber(employeeNumber)
				.name(request.getName())
				.department(request.getDepartment())
				.team(request.getTeam())
				.position(request.getPosition())
				.email(request.getEmail())
				.hireDate(hireDate)
				.currYear(currentYear)
				.currTotalLeaveDays(currentYearFloat)
				.approverId(request.getApproverId())
				.build();
    	
    	employeeRepository.save(employee);
    	
        return RegisterDto.RegisterResponse.builder()
                .employeeId(employee.getEmployeeId())
                .loginId(employee.getEmployeeNumber())
                .build();
	}

    @Transactional
    public SignUpDto.SignUpResponse signUp(SignUpDto.SignUpRequest request) {
        // 1. 사번으로 관리자가 등록해둔 직원 정보 조회
        Employee employee = employeeRepository.findByEmployeeNumber(request.getEmployeeNumber())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "등록되지 않은 사번입니다."));

        // 2. 이미 가입된 사원인지 확인 (password가 이미 채워져 있으면 가입 완료 상태)
        if (employee.getPassword() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 사원입니다.");
        }

        // 3. 비밀번호 암호화 후 저장
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // Dirty Checking(변경 감지)
        // 명시적으로 save()를 호출하지 않아도, @Transactional 범위 안에서 조회한 Entity의 필드를 변경하면 트랜잭션이 끝날 때 자동으로 Update
        employee.completeSignUp(encodedPassword);
        
        // 4. Role 설정: teamData에 값이 있으면 ADMIN, 없으면 EMPLOYEE
        if (employee.getTeamData() != null && !employee.getTeamData().isEmpty()) {
            employee.setRole(Role.ADMIN);
        } else {
            employee.setRole(Role.EMPLOYEE);
        }

        return SignUpDto.SignUpResponse.builder()
                .employeeId(employee.getEmployeeId())
                .name(employee.getName())
                .build();
    }

    public SignInDto.SignInResponse signIn(SignInDto.SignInRequest request) {
        // 1. loginId(=사번)로 직원 조회
        Employee employee = employeeRepository.findByEmployeeNumber(request.getEmployeeNumber())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "사번 또는 비밀번호가 일치하지 않습니다."));

        // 2. 비밀번호 일치 여부 확인
        if (!passwordEncoder.matches(request.getPassword(), employee.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "사번 또는 비밀번호가 일치하지 않습니다.");
        }

        // 3. 현재 연도 연차일수 계산 및 설정
        employeeLeaveService.calculateAndSetCurrentYearLeaveDays(employee);

        // 4. JWT 발급
        String token = jwtProvider.generateToken(employee.getEmployeeId(), employee.getRole().name());

        return SignInDto.SignInResponse.builder()
                .token(token)
                .employeeId(employee.getEmployeeId())
                .name(employee.getName())
                .role(employee.getRole().name())
                .build();
    }
}