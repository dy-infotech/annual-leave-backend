package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import com.dyinfotech.annualleavebackend.common.security.PasswordPolicy;
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
import com.dyinfotech.annualleavebackend.service.TeamService.ManagedTeam;

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
    private final AuthRateLimitService authRateLimitService;
    
    private final Clock clock;
    
    private final JavaMailSender mailSender; // 이메일 발송 객체 추가
    @Value("${spring.mail.username}")
    private String mailFrom;
    
    public void checkAdmin(Long employeeId) {
    	Employee employee = employeeRepository.findById(employeeId)
    									.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));
    	if (!employee.isActive(LocalDate.now(clock)) || !teamService.isTeamManager(employeeId)) {
    		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "인가되지 않은 사용자입니다. 다시 로그인해주세요.");
    	}
    }
    
    public void checkPersonnelAuthority(Long employeeId) {
        Employee requester = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));
        if (!requester.isActive(LocalDate.now(clock)) || !requester.hasPersonnelAuthority()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "인사권을 가진 관리자가 아닙니다.");
        }
    }
    
    public CompletableFuture<Void> syncFcmToken(
            Long employeeId,
            FcmTokenDto.FcmTokenRequest request) {
        return syncFcmToken(employeeId, request, null);
    }

    public CompletableFuture<Void> syncFcmToken(
            Long employeeId,
            FcmTokenDto.FcmTokenRequest request,
            String authSessionMarker) {
		// Web SSO 세션 marker까지 binding해야 같은 계정의 이전 세션 logout이
		// 새 세션의 FCM token을 삭제하지 못한다. legacy/native client는 null을 허용한다.
        return notificationService.syncToken(
            employeeId,
            request.getFcmToken(),
            request.getDeviceOs(),
            authSessionMarker
        );
    }
    
    @Transactional(readOnly = true)
    public RegisterCommonDto.RegisterCommonResponse getCommonData(Long employeeId) {
        Employee requester = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));
        PositionType requesterPosition = PositionType.getType(requester.getPosition());
        if (requesterPosition == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "직급 정보가 잘못되었습니다.");
        }

        var departments = departmentService.findAll();
        java.util.Collection<String> accessibleTeams;
        if (requester.hasPersonnelAuthority()) {
            accessibleTeams = teamService.findAllTeamInfo().stream()
                    .filter(team -> Boolean.TRUE.equals(team.enabled()))
                    .map(team -> team.teamName())
                    .collect(Collectors.toSet());
        } else {
            accessibleTeams = teamService.findManagedTeams(employeeId).stream()
                    .flatMap(team -> teamService.getSelfAndDescendants(team.teamName()).stream())
                    .map(ManagedTeam::teamName)
                    .collect(Collectors.toSet());
        }

        var accessibleTeamInfo = teamService.findAllTeamInfo().stream()
                .filter(team -> Boolean.TRUE.equals(team.enabled()))
                .filter(team -> accessibleTeams.contains(team.teamName()))
                .map(team -> RegisterCommonDto.TeamOptionResponse.builder()
                        .teamId(team.teamId())
                        .teamName(team.teamName())
                        .departmentId(team.departmentId())
                        .departmentName(departments.stream()
                                .filter(department -> Objects.equals(department.departmentId(), team.departmentId()))
                                .map(department -> department.departmentName())
                                .findFirst()
                                .orElse(null))
                        .build())
                .toList();

        return RegisterCommonDto.RegisterCommonResponse.builder()
                .department(departments.stream()
                        .map(department -> department.departmentName())
                        .toList())
                .accessibleTeam(accessibleTeams)
                .accessibleTeamInfo(accessibleTeamInfo)
                .position(Arrays.asList(PositionType.values()).stream()
                        .filter(position -> position.ordinal() < requesterPosition.ordinal())
                        .map(PositionType::getName)
                        .toList())
                .build();
    }
    
    @Transactional
    public RegisterDto.RegisterResponse registerEmployee(Long employeeId, RegisterDto.RegisterRequest request) {
        LocalDate now = LocalDate.now(clock);
        String currentYear = String.valueOf(now.getYear());

        Employee approver = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        if (employeeRepository.existsByEmployeeNumber(request.getEmployeeNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 사번입니다.");
        }

        Department department = departmentService.findByDepartmentName(request.getDepartment())
                .orElseThrow(() -> {
                    String errorMsg = "일치하는 부서 정보가 없습니다. departmentName:" + request.getDepartment();
                    log.error(errorMsg);
                    return new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg);
                });

        PositionType targetPosition = PositionType.getType(request.getPosition());
        int validationResult = approver.getManageTypeByDepartmentAndPosition(department, targetPosition);
        if (!ManageType.IS_VALID_DEPARTMENT.contains(validationResult)) {
            String errorMsg;
            String detailMsg = "approverId: " + employeeId
                    + ", approverDepartment: " + approver.getDepartmentName()
                    + ", requestedDepartment: " + department.getDepartmentName();
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
            String detailMsg = "approverId: " + employeeId
                    + ", approverPosition: " + approver.getPosition()
                    + ", targetPosition: " + targetPosition;
            log.error(errorMsg + " " + detailMsg);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg);
        }

        // 팀의 존재 여부와 팀 관리자 존재 여부를 분리해서 판단한다.
        Entry<Integer, String> teamData = teamService.getTeamManagerData(request.getTeam(), approver);
        if (!ManageType.IS_TEAM_MANAGER.contains(teamData.getKey())
                && !ManageType.IS_NEW_TEAM.contains(teamData.getKey())) {
            String errorMsg = "해당 팀을 관리하는 관리자가 아닙니다.";
            String detailMsg = "team : " + request.getTeam()
                    + ", approverId : " + employeeId
                    + ", approverTeam=[" + teamData.getValue() + "]";
            log.error(errorMsg + " " + detailMsg);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, errorMsg);
        }

        if (ManageType.IS_NEW_TEAM.contains(teamData.getKey())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "존재하지 않는 팀입니다. 부서 및 팀 관리 화면에서 팀을 먼저 생성해주세요.");
        }

        boolean makeAdminAccount = false;
        if (Role.isAdmin(request.getRole())) {
            if (approver.hasPersonnelAuthority()) {
                makeAdminAccount = true;
            } else {
                String errorMsg = "해당 팀의 관리자로 등록할 권한이 부족합니다.";
                log.error(errorMsg + " team: {}, approverId: {}", request.getTeam(), employeeId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, errorMsg);
            }
        }

        Team team = teamService.findByTeamName(request.getTeam())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "팀 정보가 없습니다."));

        if (!team.getDepartment().getDepartmentId().equals(department.getDepartmentId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "선택한 팀은 선택한 부서에 속하지 않습니다.");
        }

        if (!makeAdminAccount) {
            teamService.requireActiveManager(team.getTeamId());
        }

        Long plannedParentTeamId = null;
        Set<Long> registrationTeamLocks = new java.util.HashSet<>();
        registrationTeamLocks.add(team.getTeamId());
        if (makeAdminAccount) {
            plannedParentTeamId = teamService.resolveParentTeamId(request.getTeam())
                    .orElseGet(teamService::resolveDefaultParentTeamId);
            registrationTeamLocks.add(plannedParentTeamId);
        }

        // 직원 등록 권한은 PM 계층/직급/부서 상태에 의존한다. 조직 변경과 동일 mutex 아래에서
        // 최신 관리자 row와 DB hierarchy를 다시 확인해 요청 대기 중 권한 회수 race를 막는다.
        teamService.lockHierarchyForUpdate();
        teamService.lockTeamsForUpdate(registrationTeamLocks);

        var currentTeamInfo = teamService.findTeamInfoFromDatabase(request.getTeam())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "팀 정보가 동시에 변경되었습니다. 다시 조회해주세요."));
        if (!Objects.equals(currentTeamInfo.teamId(), team.getTeamId())
                || !Objects.equals(currentTeamInfo.departmentId(), department.getDepartmentId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "팀의 소속 정보가 동시에 변경되었습니다. 다시 조회해주세요.");
        }

        approver = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 관리자입니다."));
        if (!approver.isActive(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "퇴사 처리된 관리자는 사원을 등록할 수 없습니다.");
        }

        int currentValidation = approver.getManageTypeByDepartmentAndPosition(department, targetPosition);
        if (!ManageType.IS_VALID_DEPARTMENT.contains(currentValidation)
                || !ManageType.IS_VALID_POSITION.contains(currentValidation)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "관리자의 현재 부서/직급 권한으로 사원을 등록할 수 없습니다.");
        }
        if (!PositionType.isCEO(PositionType.getType(approver.getPosition()))
                && !teamService.isManagerForTeamFromDatabase(
                        approver.getEmployeeId(),
                        team.getTeamId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "현재 관리 범위에 속하지 않는 팀에는 사원을 등록할 수 없습니다.");
        }
        if (Role.isAdmin(request.getRole()) && !approver.hasPersonnelAuthority()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "관리자 계정을 등록할 현재 인사권이 없습니다.");
        }

        // 최초 중복 검사는 lock 대기 전에 수행되므로 동시 등록이 먼저 커밋됐을 수 있다.
        if (employeeRepository.existsByEmployeeNumber(request.getEmployeeNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 사번입니다.");
        }

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

        employeeService.saveEmployee(employee);

        if (makeAdminAccount) {
            teamService.addManager(request.getTeam(), employee.getEmployeeId(), plannedParentTeamId);
            teamService.refreshApproverIds(
                    employee,
                    Set.of(),
                    Map.of(team.getTeamId(), plannedParentTeamId));
        } else {
            teamService.refreshApproverIds(employee);
        }

        teamService.validateFutureApprovalCoverage(Set.of(team.getTeamId()));
        return RegisterDto.RegisterResponse.builder()
                .employeeId(employee.getEmployeeId())
                .employeeNumber(employee.getEmployeeNumber())
                .build();
    }

    @Transactional
    public SignUpDto.SignUpResponse signUp(SignUpDto.SignUpRequest request) {
        authRateLimitService.checkPublicAuth("signup", request.getEmployeeNumber());

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
        // BCrypt는 신규 encode 입력을 UTF-8 72 bytes까지만 허용한다.
        // @Size(max=72)는 문자 수 기준이므로 멀티바이트 비밀번호를 별도로 검증한다.
        if (!PasswordPolicy.isBcryptEncodable(request.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }

        // 동시 가입은 password IS NULL CAS로 최초 1건만 성공시킨다.
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        if (!employeeService.completeSignUpIfUnregistered(
                employee.getEmployeeId(), encodedPassword)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 가입되었거나 다른 가입 요청이 먼저 처리되었습니다.");
        }

        return SignUpDto.SignUpResponse.builder()
                .employeeId(employee.getEmployeeId())
                .name(employee.getName())
                .build();
    }

    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    private static final DateTimeFormatter YYYY_MM_DD_HH_MM_SS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public void validateLogin(Employee employee, String password) throws ResponseStatusException {
        int loginFailMaxCount = basisDataFactory.getAsInteger(BasisDataType.LOGIN_FAIL_MAX_COUNT).orElse(30);
        int loginUnblockHour = basisDataFactory.getAsInteger(BasisDataType.LOGIN_UNBLOCK_HOUR).orElse(24);
        LocalDateTime now = LocalDateTime.now(clock);

        // 오래된 실패 횟수는 누적하지 않는다. 기존 구현은 29회가 수개월 뒤에도 남아
        // 한 번의 오타로 24시간 잠길 수 있었다.
        if (employee.getAccessCount() > 0 && employee.getAccessedAt() != null
                && now.isAfter(employee.getAccessedAt().plus(loginUnblockHour, ChronoUnit.HOURS))) {
            employeeService.resetAccessCount(employee.getEmployeeId(), now);
            employee.initAccessCount(now);
        }

        String currentPassword = employee.getPassword();
        boolean isBcrypt = StringUtils.hasText(currentPassword)
                && passwordEncoder instanceof BCryptPasswordEncoder
                && BCRYPT_PATTERN.matcher(currentPassword).matches();

        boolean isPasswordValid = isBcrypt
                ? passwordEncoder.matches(password, currentPassword)
                : Objects.equals(password, currentPassword);

        if (!isPasswordValid) {
            if (employee.getAccessCount() >= loginFailMaxCount) {
                LocalDateTime unblockTime = employee.getAccessedAt() == null
                        ? now.plus(loginUnblockHour, ChronoUnit.HOURS)
                        : employee.getAccessedAt().plus(loginUnblockHour, ChronoUnit.HOURS);
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 실패 " + loginFailMaxCount + "번째로 " + loginUnblockHour
                                + "시간동안 로그인이 불가능합니다. 로그인 가능 시각 : "
                                + unblockTime.format(YYYY_MM_DD_HH_MM_SS));
            }

            employeeService.increaseAccessCount(employee.getEmployeeId(), now);
            employee.increaseAccessCount(now);
            log.error("비밀번호 에러 employeeId : {}, failCount : {}",
                    employee.getEmployeeId(), employee.getAccessCount());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사번 또는 비밀번호가 일치하지 않습니다.");
        }

        // 실패 횟수는 brute-force 완화용 상태이지 제3자가 계정을 장시간 봉쇄하는
        // 권한이 되어서는 안 된다. 올바른 비밀번호가 확인되면 잠금 상태라도 복구한다.
        employeeService.resetAccessCount(employee.getEmployeeId(), now);
        employee.initAccessCount(now);

        if (!isBcrypt && passwordEncoder instanceof BCryptPasswordEncoder) {
            String encodedPassword = passwordEncoder.encode(password);
            if (!employeeService.compareAndSetPassword(
                    employee.getEmployeeId(), currentPassword, encodedPassword)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "비밀번호 정보가 동시에 변경되었습니다. 다시 로그인해주세요.");
            }
            // 이후 JWT credentialVersion이 DB와 동일한 hash를 사용하도록 detached 객체도 맞춘다.
            employee.changePassword(encodedPassword);
        }

        EmployeeLeaveService.LeaveYearState leaveYearState =
                employeeLeaveService.ensureCurrentLeaveYear(employee.getEmployeeId());
        employee.setPrevYear(leaveYearState.prevYear());
        employee.setPrevYearLeaveDays(leaveYearState.prevTotalLeaveDays());
        employee.setCurrYear(leaveYearState.currYear());
        employee.setCurrYearLeaveDays(leaveYearState.currTotalLeaveDays());

        // 로그인 self-heal은 detached Employee 전체를 merge하지 않고 approver FK만 targeted update한다.
        Long storedApproverId = employee.getApproverId();
        Set<Long> currentApproverIds = teamService.resolveCurrentApproverIds(employee);
        if (!currentApproverIds.isEmpty()
                && (storedApproverId == null || !currentApproverIds.contains(storedApproverId))) {
            Long currentApproverId = currentApproverIds.stream().min(Long::compareTo).orElseThrow();
            employeeService.updateApproverIfUnchanged(
                    employee.getEmployeeId(),
                    employee.getTeamId(),
                    storedApproverId,
                    currentApproverId);
        }
    }
    
    public SignInDto.SignInResponse signIn(SignInDto.SignInRequest request) {
        authRateLimitService.checkSignIn(request.getEmployeeNumber());

        Employee employee = employeeService.getEmployee(request.getEmployeeNumber())
                .orElseThrow(() -> {
                    log.error("사번이 존재하지 않습니다. employeeNumber: " + request.getEmployeeNumber());
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사번 또는 비밀번호가 일치하지 않습니다.");
                });

        if (!employee.isActive(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "퇴사 처리된 사원입니다.");
        }

        if (employee.getPassword() == null) {
            log.error("사용 등록이 되지 않은 사원입니다. employeeNumber: " + request.getEmployeeNumber());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용 등록이 되지 않은 사원입니다.");
        }

        validateLogin(employee, request.getPassword());
        authRateLimitService.clearSignIn(request.getEmployeeNumber());
        return issueAccessToken(employee);
    }

    /**
     * 비밀번호 검증 직후부터 refresh session 발급 사이에 계정 상태가 바뀌는 race를 닫는다.
     * RefreshTokenService의 transaction에 참여해 employee row를 잠근 채,
     * 최초 로그인 검증 때 발급한 access token의 credentialVersion과 현재 password 상태를 대조한다.
     */
    @Transactional
    public SignInDto.SignInResponse revalidateSignInAccess(
            Long employeeId,
            String validatedAccessToken) {
        String expectedCredentialVersion =
                jwtProvider.getCredentialVersion(validatedAccessToken);

        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "현재 직원 정보를 확인할 수 없습니다."));

        if (!employee.isActive(LocalDate.now(clock)) || employee.getPassword() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 처리 중 계정 상태가 변경되었습니다. 다시 로그인해주세요.");
        }

        String currentCredentialVersion =
                jwtProvider.createCredentialVersion(employee.getPassword());
        if (expectedCredentialVersion == null
                || !Objects.equals(expectedCredentialVersion, currentCredentialVersion)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 처리 중 인증 정보가 변경되었습니다. 다시 로그인해주세요.");
        }

        return issueAccessToken(employee);
    }

    @Transactional(readOnly = true)
    public SignInDto.SignInResponse issueCurrentAccessToken(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "현재 직원 정보를 확인할 수 없습니다."));

        if (!employee.isActive(LocalDate.now(clock))) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "퇴사 처리된 사원입니다.");
        }
        if (employee.getPassword() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "사용 등록이 해제된 사용자입니다.");
        }

        return issueAccessToken(employee);
    }

    private SignInDto.SignInResponse issueAccessToken(Employee employee) {
        EmployeeAuthorityResolver roleResolver =
                employeeLeaveService.createAuthorityResolver(employee.getEmployeeId());
        Role role = roleResolver.resolveRole(employee.getEmployeeId());
        String credentialVersion = jwtProvider.createCredentialVersion(employee.getPassword());
        String token = jwtProvider.generateToken(
                employee.getEmployeeId(),
                role.name(),
                credentialVersion);

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
        authRateLimitService.checkPublicAuth("find-email-by-id", request.getName());
        return createEmailResponse(CacheConfig.EMAIL_BY_NAME_CACHE.get(
                request.getName(), name -> employeeService.findEmailsByName(name)));
    }

    @Transactional(readOnly = true)
    public EmailResponse findEmails(FindDataDto.FindEmailByEmployeeNumberRequest request) {
        authRateLimitService.checkPublicAuth(
                "find-email-by-employee-number", request.getEmployeeNumber());
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
        public void findId(FindDataDto.FindIdRequest request) {
        authRateLimitService.checkRecovery("find-id:" + request.getName() + ":" + request.getEmail());
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
 
 
    // 비밀번호 재설정은 PasswordResetService의 일회용 token 흐름으로만 처리한다.
    
    public CompletableFuture<Void> logout(Long employeeId, String fcmToken) {
        return logout(employeeId, fcmToken, null);
    }

    public CompletableFuture<Void> logout(
            Long employeeId,
            String fcmToken,
            String expectedAuthSessionMarker) {
        if (fcmToken != null && !fcmToken.isBlank()) {
            return notificationService.logoutToken(
                    fcmToken,
                    employeeId,
                    expectedAuthSessionMarker);
        }
        // fcmToken 없으면 서버 측 정리 불필요 — 클라가 토큰 폐기 (200 반환)
        return CompletableFuture.completedFuture(null);
    }
}
