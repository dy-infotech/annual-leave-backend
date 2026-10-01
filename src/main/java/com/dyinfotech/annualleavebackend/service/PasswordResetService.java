package com.dyinfotech.annualleavebackend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.common.security.PasswordPolicy;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.PasswordResetToken;
import com.dyinfotech.annualleavebackend.dto.FindDataDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.PasswordResetTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int RESET_TOKEN_MINUTES = 15;

    private final EmployeeService employeeService;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmployeeRepository employeeRepository;
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimitService authRateLimitService;
    private final JavaMailSender mailSender;
    private final Clock clock;

    @Value("${spring.mail.username}")
    private String mailFrom;

    public void requestReset(FindDataDto.FindPasswordRequest request) {
        authRateLimitService.checkRecovery("forgot-password:" + request.getEmployeeNumber());

        String realEmail = CacheConfig.EMAIL_BY_EMPLOYEE_NUMBER_CACHE.get(
                request.getEmployeeNumber(),
                employeeService::findEmailsByEmployeeNumber);
        String requestedEmail = request.getEmail() == null ? null : request.getEmail().trim();
        if (realEmail == null || requestedEmail == null || !realEmail.equalsIgnoreCase(requestedEmail)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당되는 유저를 찾을 수 없습니다.");
        }

        // 사번과 이메일로 재설정 대상 계정을 확인한다
        Employee employee = employeeService.getEmployee(request.getEmployeeNumber(), realEmail)
                .filter(Employee::isRegisted)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "해당되는 유저를 찾을 수 없습니다."));

        LocalDateTime now = LocalDateTime.now(clock);
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hash(rawToken);
        Long candidateEmployeeId = employee.getEmployeeId();

        // 만료 토큰 정리는 직원 잠금과 분리해 먼저 처리한다
        transactionTemplate.executeWithoutResult(status -> tokenRepository.deleteExpired(now));

        // 직원 정보를 잠근 뒤 기존 토큰을 지우고 새 토큰을 발급한다
        PreparedReset prepared = transactionTemplate.execute(status -> {
            Employee lockedEmployee = employeeRepository.findByIdForUpdate(candidateEmployeeId)
                    .filter(Employee::isRegisted)
                    .filter(current -> current.getEmail() != null
                            && current.getEmail().equalsIgnoreCase(requestedEmail))
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "해당되는 유저를 찾을 수 없습니다."));

            tokenRepository.deleteUnusedByEmployeeId(lockedEmployee.getEmployeeId());
            PasswordResetToken saved = tokenRepository.saveAndFlush(new PasswordResetToken(
                    lockedEmployee.getEmployeeId(),
                    tokenHash,
                    now.plusMinutes(RESET_TOKEN_MINUTES),
                    now));

            return new PreparedReset(
                    saved.getTokenId(),
                    lockedEmployee.getEmployeeId(),
                    lockedEmployee.getEmail());
        });

        if (prepared == null || prepared.tokenId() == null) {
            throw new IllegalStateException("비밀번호 재설정 토큰 저장에 실패했습니다.");
        }

        // 메일 발송에 실패하면 방금 발급한 토큰을 제거한다
        try {
            sendResetMail(prepared.employeeId(), prepared.recipient(), rawToken);
        } catch (RuntimeException e) {
            try {
                transactionTemplate.executeWithoutResult(
                        status -> tokenRepository.deleteById(prepared.tokenId()));
            } catch (RuntimeException cleanupError) {
                log.error(
                        "메일 발송 실패 후 재설정 토큰 정리에도 실패했습니다. tokenId={}",
                        prepared.tokenId(),
                        cleanupError);
            }
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이메일 발송 중 오류가 발생했습니다.",
                    e);
        }
    }

    private void sendResetMail(Long employeeId, String recipient, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(recipient);
        message.setSubject("[(주)디와이정보기술] 휴가관리 시스템 비밀번호 재설정");
        message.setText("안녕하세요. (주)디와이정보기술 휴가관리 시스템입니다.\n\n"
                + "아래 재설정 토큰을 " + RESET_TOKEN_MINUTES + "분 이내에 입력해 새 비밀번호를 설정해 주세요.\n"
                + "재설정 토큰: " + rawToken + "\n\n"
                + "본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        try {
            mailSender.send(message);
        } catch (RuntimeException e) {
            log.error("비밀번호 재설정 메일 발송 실패. employeeId={}", employeeId, e);
            throw e;
        }
    }

    private record PreparedReset(Long tokenId, Long employeeId, String recipient) {
    }

    @Transactional
    public void confirmReset(FindDataDto.ResetPasswordRequest request) {
        String tokenHash = hash(request.getToken());
        authRateLimitService.checkRecovery("reset-password:" + tokenHash);

        LocalDateTime now = LocalDateTime.now(clock);
        // 토큰 대상 직원을 잠근 뒤 토큰 상태를 다시 확인한다
        PasswordResetToken candidate = tokenRepository
                .findByTokenHashAndConsumedAtIsNull(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "유효하지 않은 재설정 토큰입니다."));

        Employee employee = employeeRepository.findByIdForUpdate(candidate.getEmployeeId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        PasswordResetToken token = tokenRepository.findUnusedForUpdate(tokenHash)
                .filter(current -> current.getEmployeeId().equals(employee.getEmployeeId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "유효하지 않은 재설정 토큰입니다."));

        if (token.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "재설정 토큰이 만료되었습니다.");
        }

        if (!PasswordPolicy.isBcryptEncodable(request.getNewPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "새 비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }

        // 비밀번호를 변경하고 기존 Refresh 세션을 폐기한다
        String expectedPassword = employee.getPassword();
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        if (!employeeService.compareAndSetPassword(
                employee.getEmployeeId(), expectedPassword, encodedPassword)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "비밀번호가 다른 요청에 의해 변경되었습니다. 재설정을 다시 요청해주세요.");
        }

        employeeService.revokeRefreshSessions(employee.getEmployeeId(), "PASSWORD_RESET");
        token.consume(now);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
