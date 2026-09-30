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
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.PasswordResetToken;
import com.dyinfotech.annualleavebackend.dto.FindDataDto;
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
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimitService authRateLimitService;
    private final JavaMailSender mailSender;
    private final Clock clock;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Transactional
    public void requestReset(FindDataDto.FindPasswordRequest request) {
        authRateLimitService.checkRecovery("forgot-password:" + request.getEmployeeNumber());

        String realEmail = CacheConfig.EMAIL_BY_EMPLOYEE_NUMBER_CACHE.get(
                request.getEmployeeNumber(),
                employeeService::findEmailsByEmployeeNumber);
        String requestedEmail = request.getEmail() == null ? null : request.getEmail().trim();
        if (realEmail == null || requestedEmail == null || !realEmail.equalsIgnoreCase(requestedEmail)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당되는 유저를 찾을 수 없습니다.");
        }

        Employee employee = employeeService.getEmployee(request.getEmployeeNumber(), realEmail)
                .filter(Employee::isRegisted)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "해당되는 유저를 찾을 수 없습니다."));

        LocalDateTime now = LocalDateTime.now(clock);
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hash(rawToken);

        tokenRepository.deleteExpired(now);
        tokenRepository.deleteUnusedByEmployeeId(employee.getEmployeeId());
        tokenRepository.saveAndFlush(new PasswordResetToken(
                employee.getEmployeeId(),
                tokenHash,
                now.plusMinutes(RESET_TOKEN_MINUTES),
                now));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(employee.getEmail());
        message.setSubject("[(주)디와이정보기술] 휴가관리 시스템 비밀번호 재설정");
        message.setText("안녕하세요. (주)디와이정보기술 휴가관리 시스템입니다.\n\n"
                + "아래 재설정 토큰을 " + RESET_TOKEN_MINUTES + "분 이내에 입력해 새 비밀번호를 설정해 주세요.\n"
                + "재설정 토큰: " + rawToken + "\n\n"
                + "본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        try {
            mailSender.send(message);
        } catch (RuntimeException e) {
            log.error("비밀번호 재설정 메일 발송 실패. employeeId={}", employee.getEmployeeId(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이메일 발송 중 오류가 발생했습니다.",
                    e);
        }
    }

    @Transactional
    public void confirmReset(FindDataDto.ResetPasswordRequest request) {
        String tokenHash = hash(request.getToken());
        authRateLimitService.checkRecovery("reset-password:" + tokenHash);

        LocalDateTime now = LocalDateTime.now(clock);
        PasswordResetToken token = tokenRepository.findUnusedForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "유효하지 않은 재설정 토큰입니다."));

        if (token.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "재설정 토큰이 만료되었습니다.");
        }

        Employee employee = employeeService.getEmployeeList(java.util.List.of(token.getEmployeeId()))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

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
