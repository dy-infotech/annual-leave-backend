package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage; // 추가됨
import org.springframework.mail.javamail.JavaMailSender; // 추가됨
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.common.security.jwt.JwtProvider;
import com.dyinfotech.annualleavebackend.common.type.BasisDataType;
import com.dyinfotech.annualleavebackend.common.type.DepartmentType;
import com.dyinfotech.annualleavebackend.common.type.ManageType;
import com.dyinfotech.annualleavebackend.common.type.PositionType;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.common.util.MaskingUtils;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.dto.FcmTokenDto;
import com.dyinfotech.annualleavebackend.dto.FindDataDto; // 추가됨
import com.dyinfotech.annualleavebackend.dto.FindDataDto.EmailResponse;
import com.dyinfotech.annualleavebackend.dto.RegisterCommonDto;
import com.dyinfotech.annualleavebackend.dto.RegisterDto;
import com.dyinfotech.annualleavebackend.dto.SignInDto;
import com.dyinfotech.annualleavebackend.dto.SignUpDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.projection.EmployeeNumberEmail;
import com.dyinfotech.annualleavebackend.service.EmployeeLeaveService.EmployeeAuthorityResolver;

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
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;
    private final TeamService teamService;
    
    private final Clock clock;
    
    private final JavaMailSender mailSender; // 이메일 발송 객체 추가
    @Value("${spring.mail.username}")
    private String mailFrom;
    
    public void checkAdmin(Long employeeId) {
    	if (!teamService.isTeamManager(employeeId)) {
    		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "인가되지 않은 사용자입니다. 다시 로그인해주세요.");
    	}
    }
    
    @Transactional
    public void syncFcmToken(Long employeeId, FcmTokenDto.FcmTokenRequest request) {
		// DB 저장(UPSERT) 및 구글 토픽 비동기 구독 실행
        notificationService.syncToken(
            employeeId,
            request.getFcmToken(), 
            request.getDeviceOs()
        );
    }
    
    @Transactional(readOnly = true)
    public RegisterCommonDto.RegisterCommonResponse getCommonData(Long employeeId) {
    	Employee requester = employeeRepository.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));
    	PositionType requesterPosition = PositionType.getType(requester.getPosition());
    	return RegisterCommonDto.RegisterCommonResponse.builder()
    													.department(departmentService.findAll().stream()
    																							.map(Department::getDepartmentName)
    																							.toList())
    													.accessibleTeam(requester.getTeams().stream()
		    																				.flatMap(team -> teamService.getSelfAndDescendants(team.getTeam().getTeamName()).stream())
		    																				.map(e -> e.getTeam().getTeamName())
		    																				.collect(Collectors.toSet()))
    													.position(Arrays.asList(PositionType.values()).stream()
    																									.filter(e -> e.ordinal() < requesterPosition.ordinal())
    																									.map(PositionType::getName)
    																									.toList())
    													.build();
    }
    
    @Transactional
    public RegisterDto.RegisterResponse registerEmployee(Long employeeId, RegisterDto.RegisterRequest request) {
    	// 사번 채번용 접두사 정보 검증 (서버 데이터)
    	LocalDate now = LocalDate.now(clock);
    	String currentYear = String.valueOf(now.getYear());
//    	String prefix = basisDataFactory.getAsString(BasisDataType.EMPLOYEE_NUMBER_PREFIX)
//    									.orElseThrow(() -> {
//    										String errorMsg = "사번 접두사 정보가 없습니다. target: BasisDataType." + BasisDataType.EMPLOYEE_NUMBER_PREFIX;
//    							    		log.error(errorMsg);
//    										return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, errorMsg);
//    									})
//    									.replace("#{YEAR}", currentYear);
    	
    	// 현재 승인자 직급과 신청받은 직급을 비교
    	Employee approver = employeeRepository.findById(employeeId)
    											.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));
    	
    	// 부서와 직급 검증
    	Department department = departmentService.findByDepartmentName(request.getDepartment()).orElseThrow(() -> {
			String errorMsg = "일치하는 부서 정보가 없습니다. departmentName:" + request.getDepartment();
    		log.error(errorMsg);
			return new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg);
    	});
    	PositionType targetPosition = PositionType.getType(request.getPosition());
    	int validationResult = approver.getManageTypeByDepartmentAndPosition(department, targetPosition);
    	if (!ManageType.IS_VALID_DEPARTMENT.contains(validationResult)) {
    		String errorMsg;
    		String detailMsg = "approverId: " + employeeId + "approverDepartment: " + approver.getDepartment() + ", requestedDepartment: " + department.getDepartmentName();
    		DepartmentType parent = DepartmentType.getParentDepartmentType();
    		if (parent.equals(DepartmentType.getType(department.getDepartmentName()))) {
    			errorMsg = parent.getName() + " 부서는 " + PositionType.CEO.getName() + "만 등록할 수 있습니다.";
    			detailMsg += ", approverPosition: " + approver.getPosition();
    		} else {
    			errorMsg = "승인자의 부서와 동일한 부서만 선택할 수 있습니다.";
    		}
    		log.error(errorMsg + " " + detailMsg);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg);
    	}
    	if (!ManageType.IS_VALID_POSITION.contains(validationResult)) {
    		String errorMsg = "나와 동등 또는 상위 직급을 설정했거나 직급 정보가 잘못되었습니다.";
    		String detailMsg = "approverId: " + employeeId + "approverPosition: " + approver.getPosition() + ", targetPosition: " + targetPosition;
    		log.error(errorMsg + " " + detailMsg);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg);
    	}
    	
    	// 팀 정보와 관리자 매칭
    	Entry<Integer, String> teamData = teamService.getTeamManagerData(request.getTeam(), approver);
    	if (!ManageType.IS_TEAM_MANAGER.contains(teamData.getKey()) && !ManageType.IS_NEW_TEAM.contains(teamData.getKey())) {
    		String errorMsg = "해당 팀을 관리하는 관리자가 아닙니다.";
    		String detailMsg = "team : " + request.getTeam() + ",approverId : " + employeeId + ",approverTeam=[" + teamData.getValue() + "]";
    		log.error(errorMsg + " " + detailMsg);
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, errorMsg);
    	}
    	
    	// 팀의 관리자로 등록되는 건지 확인
    	boolean makeAdminAccount = false;
    	if (Role.isAdmin(request.getRole())) {
    		if (approver.hasPersonnelAuthority()) {
    			makeAdminAccount = true;
    		} else {
        		String errorMsg = "해당 팀의 관리자로 등록할 권한이 부족합니다.";
        		String detailMsg = "team : " + request.getTeam() + ",approverId : " + employeeId + ",approverPosition : " + approver.getPosition();
        		log.error(errorMsg + " " + detailMsg);
    			throw new ResponseStatusException(HttpStatus.FORBIDDEN, errorMsg);
    		}
    	} else if (ManageType.IS_NEW_TEAM.contains(teamData.getKey())) {
    		String errorMsg = "새로운 팀 생성 시 프로젝트 매니저부터 등록하십시오.";
    		String detailMsg = "team : " + request.getTeam() + ",role : " + request.getRole() + ",approverId : " + employeeId + ",approverPosition : " + approver.getPosition();
    		log.error(errorMsg + " " + detailMsg);
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, errorMsg);
    	}

  //  	Optional<Employee> lastPrefixEmployee = employeeService.findByPrefixEmployeeNumber(prefix);
    	
//    	// 사번 설정
//    	String formatString = "%03d";
//    	String employeeNumber = null;
//    	if (lastPrefixEmployee.isPresent()) {
//			String lastEmployeeNumber = lastPrefixEmployee.get().getEmployeeNumber();
//			int lastNumber = Integer.parseInt(lastEmployeeNumber.substring(prefix.length()));
//			employeeNumber = prefix + String.format(formatString, lastNumber + 1);
//		} else {
//			employeeNumber = prefix + String.format(formatString, 1);
//		}
//    	
    	
    	Team team;
    	// 신규 팀이 만들어져야 한다면
    	if (ManageType.IS_NEW_TEAM.contains(teamData.getKey())) {
    		team = Team.builder()
		        			.teamName(request.getTeam())
		        			.enabled(Boolean.TRUE)
		        			.build();
    		teamService.saveTeam(team);
    	} else {
    		team = teamService.findByTeamName(request.getTeam())
    							.orElseThrow(() -> {
									String errorMsg = "팀 정보가 없습니다. teamName: " + request.getTeam();
						    		log.error(errorMsg);
									return new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg);
								});
    	}
    	
    	// 근로자 정보 등록
    	LocalDate hireDate = LocalDate.parse(request.getHireDate());
    	Employee employee = Employee.builder()
				.employeeNumber(request.getEmployeeNumber())
				.name(request.getName())
				.department(department)
				.team(team)
				.position(request.getPosition())
				.email(request.getEmail())
				.hireDate(hireDate)
				.currYear(currentYear)
				.currTotalLeaveDays(employeeLeaveService.getCalculatedCurrYearLeaveDays(hireDate))
				.approver(approver)
				.build();
    	

    	// 신규 팀이 만들어져야 한다면
    	if (ManageType.IS_NEW_TEAM.contains(teamData.getKey())) {
    		teamService.saveTeam(TeamManager.builder()
									.team(team)
									.projectManager(employee)
									// XXX: 대표이사만 등록 가능하므로 대표이사 팀을 넣으면 될 것 같다. 차후에 문제가 생기면 getParentTeam으로 수정.
									.parentTeam(approver.getTeam())
									.build());
    	}
    	// 신규 팀은 아니지만 관리자로 등록되어야 한다면
    	else if (makeAdminAccount) {
    		teamService.saveTeam(TeamManager.builder()
									.team(team)
									.projectManager(employee)
									// XXX: getTeamManagerData 호출될 때 해당 팀이 존재하는 걸 확인했으므로 get(0)으로 처리한다.
									.parentTeam(teamService.findAllByTeam(request.getTeam()).get(0).getParentTeam())
									.build());
    	}
    	
    	employeeService.saveEmployee(employee);
    	
        return RegisterDto.RegisterResponse.builder()
                .employeeId(employee.getEmployeeId())
                .employeeNumber(employee.getEmployeeNumber())
                .build();
	}

    @Transactional
    public SignUpDto.SignUpResponse signUp(SignUpDto.SignUpRequest request) {
        // 1. 사번으로 관리자가 등록해둔 직원 정보 조회
        Employee employee = employeeService.getEmployee(request.getEmployeeNumber())
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

    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    private static final DateTimeFormatter YYYY_MM_DD_HH_MM_SS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public void validateLogin(Employee employee, String password) throws ResponseStatusException {
        // 로그인 실패 최대 횟수 제한
        int loginFailMaxCount = basisDataFactory.getAsInteger(BasisDataType.LOGIN_FAIL_MAX_COUNT).orElse(30);
        int loginUnblockHour = basisDataFactory.getAsInteger(BasisDataType.LOGIN_UNBLOCK_HOUR).orElse(24);
        if (employee.getAccessCount() >= loginFailMaxCount) {
        	LocalDateTime unblockTime = employee.getAccessedAt().plus(loginUnblockHour, ChronoUnit.HOURS);
        	LocalDateTime now = LocalDateTime.now(clock);
        	if (now.isAfter(unblockTime)) {
            	employeeService.resetAccessCount(employee.getEmployeeId(), now);
        	} else {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "로그인 실패 " + loginFailMaxCount + "번째로 " + loginUnblockHour + "시간동안 로그인이 불가능합니다. 로그인 가능 시각 : " + unblockTime.format(YYYY_MM_DD_HH_MM_SS));
        	}
        }
        
        String currentPassword = employee.getPassword();

        // 현재 DB에 저장된 비밀번호가 BCrypt 형식인지 확인
        boolean isBcrypt = StringUtils.hasText(currentPassword) 
			        		&& passwordEncoder instanceof BCryptPasswordEncoder 
			        		&& BCRYPT_PATTERN.matcher(currentPassword).matches();
        
        // 비밀번호 일치 여부 검증 (BCrypt와 평문 분기)
        boolean isPasswordValid;
        if (isBcrypt) {
        	isPasswordValid = passwordEncoder.matches(password, currentPassword);
        } else {
        	// 평문 데이터 마이그레이션 대상: 단순 문자열 비교
        	isPasswordValid = Objects.equals(password, currentPassword);
        }
        
        // 비밀번호가 틀린 경우 실패 처리
        LocalDateTime now = LocalDateTime.now(clock);
        if (!isPasswordValid) {
        	employeeService.increaseAccessCount(employee.getEmployeeId(), now);
        	log.error("비밀번호 에러 employeeId : {}, failCount : {}", employee.getEmployeeId(), employee.getAccessCount());
        	throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사번 또는 비밀번호가 일치하지 않습니다.");
        } else {
        	employeeService.resetAccessCount(employee.getEmployeeId(), now);
        }
        
        // 로그인 성공 && 기존이 평문이었던 경우: BCrypt로 암호화하여 DB 업데이트 (마이그레이션)
        if (!isBcrypt && passwordEncoder instanceof BCryptPasswordEncoder) {
        	employeeService.updatePassword(employee.getEmployeeId(), passwordEncoder.encode(password));
        }
        
        // 현재 연도 연차일수 계산 및 설정
        float calculatedCurrYearLeaveDays = employeeLeaveService.getCalculatedCurrYearLeaveDays(employee);
        if (employee.getCurrTotalLeaveDays() != calculatedCurrYearLeaveDays) {
        	employeeService.updateCurrTotalLeaveDays(employee.getEmployeeId(), calculatedCurrYearLeaveDays);
        }
        
        // 로그인시 현재 팀의 프로젝트 매니저가 승인자인지 확인하고, 그렇지 않은 경우 업데이트
        // (팀 소속만 변경됐다고 가정한다. 이후에 팀 변경 창이 생기면 오류가 해소되나, SQL로 별도 처리할 경우를 대비한 코드)
        teamService.refreshApproverIds(employee);
    }
    
    public SignInDto.SignInResponse signIn(SignInDto.SignInRequest request) {
        // employeeNumber(=사번)로 직원 조회
        Employee employee = employeeService.getEmployee(request.getEmployeeNumber())
                .orElseThrow(() -> {
                	log.error("사번이 존재하지 않습니다. employeeNumber: " + request.getEmployeeNumber());
                	return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사번 또는 비밀번호가 일치하지 않습니다.");
                });
        
        // 사용 등록 여부 확인
        if (employee.getPassword() == null) {
			log.error("사용 등록이 되지 않은 사원입니다. employeeNumber: " + request.getEmployeeNumber());
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용 등록이 되지 않은 사원입니다.");
		}
        
        // 로그인 횟수 검증 및 비밀번호 일치 여부 확인 (예외 발생시 바로 중단되어야 하므로 try-catch를 쓰지 않음)
        validateLogin(employee, request.getPassword());
        
        // JWT 발급
        EmployeeAuthorityResolver roleResolver = employeeLeaveService.createAuthorityResolver(employee.getEmployeeId());
        Role role = roleResolver.resolveRole(employee.getEmployeeId());
        String token = jwtProvider.generateToken(employee.getEmployeeId(), role.name());

        return SignInDto.SignInResponse.builder()
                .token(token)
                .employeeId(employee.getEmployeeId())
                .name(employee.getName())
                .role(role.name())
				.email(employee.getEmail())
                .build();
    }

    @Transactional(readOnly = true)
    public EmailResponse findEmails(FindDataDto.FindEmailByIdRequest request) {
        return createEmailResponse(CacheConfig.EMAIL_BY_NAME_CACHE.get(request.getName(), name -> employeeService.findEmailsByName(name)));
    }

    @Transactional(readOnly = true)
    public EmailResponse findEmails(FindDataDto.FindEmailByEmployeeNumberRequest request) {
        return createEmailResponse(CacheConfig.EMAIL_BY_EMPLOYEE_NUMBER_CACHE.get(request.getEmployeeNumber(), 
        																		number -> employeeService.findEmailsByEmployeeNumber(number)));
    }

    private EmailResponse createEmailResponse(List<String> emails) {
        return FindDataDto.EmailResponse.builder()
						                .maskedEmailList(
						                        emails.stream()
						                              .map(MaskingUtils::maskEmail)
						                              .toList()
						                )
						                .build();
    }
    private EmailResponse createEmailResponse(String email) {
    	return createEmailResponse(Collections.singletonList(email));
    }
    
    private void sendMail(String to, String subject, String text) throws MailException {
    	SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        
        mailSender.send(message);
    }

    @Transactional(readOnly = true)
    public void findId(FindDataDto.FindIdRequest request) {
        // 성함과 이메일로 회원 조회
    	List<String> emailList = CacheConfig.EMAIL_BY_NAME_CACHE.get(request.getName(), employeeService::findEmailsByName)
					    										.stream()
					    										.filter(email -> MaskingUtils.maskEmail(email).equals(request.getEmail()) || 
					    														email.equals(request.getEmail()))
					    										.toList();
    	if (emailList.isEmpty()) {
        	throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당되는 유저를 찾을 수 없습니다.");
    	}
        List<EmployeeNumberEmail> dataList = employeeService.findEmployeeNumberAndEmailByNameAndEmailIn(request.getName(), emailList);
        if (dataList.isEmpty()) {
        	throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당되는 유저를 찾을 수 없습니다.");
        }
        
        boolean failed = false;
        for (EmployeeNumberEmail data : dataList) {
            // 사번 이메일 전송
            String to = data.email();
            String subject = "[(주)디와이정보기술] 휴가관리 시스템 사번 조회";
        	String text = "안녕하세요. (주)디와이정보기술 휴가관리 시스템입니다.\n\n" +
                    "요청하신 사번은 " + data.employeeNumber() +"입니다.";
            try {
            	sendMail(to, subject, text);
            } catch (Exception e) {
            	failed = true;
            	log.error("사번 메일 발송 오류 from: {}, to: {}, subject: {}", mailFrom, to, subject, e);
            }
        }
        
        if (failed) {
        	StringBuilder errorMsg = new StringBuilder();
        	if (dataList.size() > 1) {
        		errorMsg.append("일부 ");
        	}
        	errorMsg.append("이메일 발송에 실패했습니다.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, errorMsg.toString());
        }
    }
 
 
    @Transactional
    public void forgotPassword(FindDataDto.FindPasswordRequest request) {
        // 사원번호와 이메일로 일치하는 회원 조회 (없으면 예외 발생)
    	String realEmail = CacheConfig.EMAIL_BY_EMPLOYEE_NUMBER_CACHE.get(request.getEmployeeNumber(), employeeService::findEmailsByEmployeeNumber);
    	Entry<HttpStatus, String> emptyUserErrorEntry = new java.util.AbstractMap.SimpleEntry<>(HttpStatus.NOT_FOUND, "해당되는 유저를 찾을 수 없습니다.");
    	if (realEmail == null) {
    		throw new ResponseStatusException(emptyUserErrorEntry.getKey(), emptyUserErrorEntry.getValue());
    	}
        Employee employee = employeeService.getEmployee(request.getEmployeeNumber(), realEmail)
                .orElseThrow(() -> new ResponseStatusException(emptyUserErrorEntry.getKey(), emptyUserErrorEntry.getValue()));

        // 임시 비밀번호 생성 (소문자 16진수 + 하이픈 10자리)
        String temporaryPassword = UUID.randomUUID().toString().substring(0, 10);

        // 임시 비밀번호 이메일 전송
        String to = employee.getEmail();
        String subject = "[(주)디와이정보기술] 휴가관리 시스템 임시 비밀번호 발급";
    	String text = "안녕하세요. (주)디와이정보기술 휴가관리 시스템입니다.\n\n" +
                "요청하신 임시 비밀번호는 다음과 같습니다.\n" +
                "임시 비밀번호: " + temporaryPassword + "\n\n" +
                "로그인 후 반드시 비밀번호를 변경해 주세요.";
        try {
        	// 메일 전송
        	sendMail(to, subject, text);
        	
            // 비밀번호 암호화 후 업데이트
            String encodedPassword = passwordEncoder.encode(temporaryPassword);
            employee.changePassword(encodedPassword);
        } catch (Exception e) {
        	log.error("패스워드 메일 발송 오류 from: {}, to: {}, subject: {}", mailFrom, to, subject, e);
        	throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이메일 발송 중 오류가 발생했습니다.", e);
        }
    }
    
    @Transactional
    public void logout(Long employeeId, String fcmToken) {
        if (fcmToken != null && !fcmToken.isBlank()) {
            notificationService.logoutToken(fcmToken, employeeId);
        }
        // fcmToken 없으면 서버 측 정리 불필요 — 클라가 토큰 폐기 (200 반환)
    }
}
