package com.dyinfotech.annualleavebackend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage; // 추가됨
import org.springframework.mail.javamail.JavaMailSender; // 추가됨
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
import com.dyinfotech.annualleavebackend.dto.ForgotPasswordDto; // 추가됨
import com.dyinfotech.annualleavebackend.dto.RegisterDto;
import com.dyinfotech.annualleavebackend.dto.SignInDto;
import com.dyinfotech.annualleavebackend.dto.SignUpDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final BasisDataFactory basisDataFactory;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final EmployeeLeaveService employeeLeaveService;
    private final NotificationService notificationService;
    private final TeamService teamService;
    
    private final JavaMailSender mailSender; // 이메일 발송 객체 추가
    @Value("${spring.mail.username}")
    private String mailFrom;
    
    @Transactional
    public RegisterDto.RegisterResponse registerEmployee(RegisterDto.RegisterRequest request) {
		// 사번 채번
    	LocalDate now = LocalDate.now();
    	Optional<BasisData> employeeNumberPrefix = basisDataFactory.get(BasisDataType.EMPlOYEE_NUMBER_PREFIX);
    	if (employeeNumberPrefix.isEmpty()) {
    		String errorMsg = "사번 접두사 정보가 없습니다. target: BasisDataType.EMPlOYEE_NUMBER_PREFIX";
    		log.error(errorMsg);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, errorMsg);
		}
    	
    	// 팀 정보와 관리자 매칭
    	Entry<Boolean, String> teamData = teamService.getTeamManagerData(request.getTeam(), request.getApproverId());
    	if (!teamData.getKey()) {
    		String errorMsg = "해당 팀을 관리하는 관리자가 아닙니다.";
    		String detailMsg = "team : " + request.getTeam() + ",approverId : " + request.getApproverId() + ",approverTeam=[" + teamData.getValue() + "]";
    		log.error(errorMsg + " " + detailMsg);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, errorMsg);
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
    	
    	// 근로자 정보 등록
    	LocalDate hireDate = LocalDate.parse(request.getHireDate());
    	Employee employee = Employee.builder()
				.employeeNumber(employeeNumber)
				.name(request.getName())
				.department(request.getDepartment())
				.team(request.getTeam())
				.position(request.getPosition())
				.email(request.getEmail())
				.hireDate(hireDate)
				.currYear(currentYear)
				.currTotalLeaveDays(employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate))
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
                .orElseThrow(() -> {
                	String errorMsg = "등록되지 않은 사번입니다.";
                	log.error(errorMsg + " " + "employeeNumber: " + request.getEmployeeNumber());
                	return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMsg);
                });

        // 2. 이미 가입된 사원인지 확인 (password가 이미 채워져 있으면 가입 완료 상태)
        if (employee.getPassword() != null) {
        	String errorMsg = "이미 가입된 사원입니다.";
        	log.error(errorMsg + " " + "employeeNumber: " + request.getEmployeeNumber());
            throw new ResponseStatusException(HttpStatus.CONFLICT, errorMsg);
        }

        // 3. 비밀번호 암호화 후 저장
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // Dirty Checking(변경 감지)
        // 명시적으로 save()를 호출하지 않아도, @Transactional 범위 안에서 조회한 Entity의 필드를 변경하면 트랜잭션이 끝날 때 자동으로 Update
        employee.completeSignUp(encodedPassword);

        return SignUpDto.SignUpResponse.builder()
                .employeeId(employee.getEmployeeId())
                .name(employee.getName())
                .build();
    }

    private static final DateTimeFormatter YYYY_MM_DD_HH_MM_SS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public void validateLogin(Employee employee, String password) throws ResponseStatusException {
        // 로그인 실패 최대 횟수 제한
        int loginFailMaxCount = basisDataFactory.getAsInteger(BasisDataType.LOGIN_FAIL_MAX_COUNT).orElse(30);
        int loginUnblockHour = basisDataFactory.getAsInteger(BasisDataType.LOGIN_UNBLOCK_HOUR).orElse(24);
        if (employee.getAccess_count() >= loginFailMaxCount) {
        	LocalDateTime unblockTime = employee.getAccessedAt().plus(loginUnblockHour, ChronoUnit.HOURS);
        	if (unblockTime.isAfter(LocalDateTime.now())) {
        		employee.initAccessCount();
        	} else {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "로그인 실패 " + loginFailMaxCount + "번째로 " + loginUnblockHour + "시간동안 로그인이 불가능합니다. 로그인 가능 시각 : " + unblockTime.format(YYYY_MM_DD_HH_MM_SS));
        	}
        }
        
        // 비밀번호 일치 여부 확인
        if (!passwordEncoder.matches(password, employee.getPassword())) {
        	employee.increaseAccessCount();
        	log.error("비밀번호 에러 employeeId : " + employee.getEmployeeId() + ",failCount : " + employee.getAccess_count());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "사번 또는 비밀번호가 일치하지 않습니다.");
        } else {
        	employee.initAccessCount();
        }
    }
    
    @Transactional
    public SignInDto.SignInResponse signIn(SignInDto.SignInRequest request) {
        // 1. employeeNumber(=사번)로 직원 조회
        Employee employee = employeeRepository.findByEmployeeNumber(request.getEmployeeNumber())
                .orElseThrow(() -> {
                	log.error("사번이 존재하지 않습니다. employeeNumber: " + request.getEmployeeNumber());
                	return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사번 또는 비밀번호가 일치하지 않습니다.");
                });
        
        // 2. 로그인 횟수 검증 및 비밀번호 일치 여부 확인 (예외 발생시 바로 중단되어야 하므로 try-catch를 쓰지 않음)
        validateLogin(employee, request.getPassword());

        // 3. 현재 연도 연차일수 계산 및 설정
        float calculatedCurrYearLeaveDays = employeeLeaveService.getCalculatedCurrYearLeaveDays(employee);
        if (employee.getCurrTotalLeaveDays() != calculatedCurrYearLeaveDays) {        	
        	employee.setCurrYearLeaveDays(calculatedCurrYearLeaveDays);
        }
        
        // 4. 로그인시 현재 팀의 프로젝트 매니저가 승인자인지 확인하고, 그렇지 않은 경우 업데이트
        //    (팀 소속만 변경됐다고 가정한다. 이후에 팀 변경 창이 생기면 오류가 해소되나, SQL로 별도 처리할 경우를 대비한 코드)
        boolean hasApproverId = false;
        Set<Long> approverIds = teamService.resolveApproverIds(employee);
        for (Long approverId: approverIds) {
        	if (approverId.equals(employee.getApproverId())) {
        		hasApproverId = true;
        		break;
        	}
        }
        if (!hasApproverId && !approverIds.isEmpty()) {
        	employee.changeApprover(approverIds.iterator().next());
        }
        
        // 5. JWT 발급
        Role role = employeeLeaveService.resolveRole(employee.getEmployeeId());
        String token = jwtProvider.generateToken(employee.getEmployeeId(), role.name());
        
        // 6. 팀 프로젝트 매니저면 FCM Token 구독 처리
        if (teamService.isTeamManager(employee.getEmployeeId())) {
        	String fcmToken = request.getFcmToken();
        	if (fcmToken != null && !fcmToken.isBlank()) {
        		// DB 저장(UPSERT) 및 구글 토픽 비동기 구독 실행
                notificationService.syncToken(
                    employee.getEmployeeId(),
                    fcmToken, 
                    request.getDeviceOs()
                );
        	} else {
        		log.warn("관리자 권한을 가졌으나 요청에 FCM 토큰이 누락되었습니다. employeeId: {}", employee.getEmployeeId());
        	}
        }

        return SignInDto.SignInResponse.builder()
                .token(token)
                .employeeId(employee.getEmployeeId())
                .name(employee.getName())
                .role(role.name())
                .build();
    }
    
    @Transactional
    public void forgotPassword(ForgotPasswordDto.Request request) {
        // 1. 사원번호와 이메일로 일치하는 회원 조회 (없으면 예외 발생시키고, 성공 케이스인 것처럼 전달해서 공격 방지)
        Employee employee = employeeRepository.findByEmployeeNumberAndEmail(request.getEmployeeNumber(), request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.OK));

        // 2. 임시 비밀번호 생성 (소문자 16진수 + 하이픈 10자리)
        String temporaryPassword = UUID.randomUUID().toString().substring(0, 10);

        // 3. 비밀번호 암호화 후 업데이트
        String encodedPassword = passwordEncoder.encode(temporaryPassword);
        employee.changePassword(encodedPassword);

        // 4. 임시 비밀번호 이메일 전송
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(employee.getEmail());
        message.setSubject("[(주)디와이정보기술] 연차관리 시스템 임시 비밀번호 발급");
        message.setText("안녕하세요. (주)디와이정보기술 연차관리 시스템입니다.\n\n" +
	                    "요청하신 임시 비밀번호는 다음과 같습니다.\n" +
	                    "임시 비밀번호: " + temporaryPassword + "\n\n" +
	                    "로그인 후 반드시 비밀번호를 변경해 주세요.");
        try {
            mailSender.send(message);
        } catch (Exception e) {
        	log.error("메일 발송 오류 from: " + message.getFrom() + ", to: " + message.getTo() + ", subject: " + message.getSubject(), e);
        	throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이메일 발송 중 오류가 발생했습니다.", e);
        }
    }
}
