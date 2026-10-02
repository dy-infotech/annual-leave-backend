package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.type.PositionType;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 시점 JWT role이 아니라 요청 시점의 조직 상태를 권한 정본으로 사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentAuthorityService {

    private final EmployeeRepository employeeRepository;
    private final TeamService teamService;
    private final Clock clock;

    public boolean isAdmin(Long employeeId) {
        if (employeeId == null) {
            return false;
        }

        return employeeRepository.findById(employeeId)
                .filter(employee -> employee.isActive(LocalDate.now(clock)))
                .map(employee -> teamService.isTeamManagerFromDatabase(employeeId))
                .orElse(false);
    }

    public boolean isCeo(Long employeeId) {
        if (employeeId == null) {
            return false;
        }

        return employeeRepository.findById(employeeId)
                .filter(employee -> employee.isActive(LocalDate.now(clock)))
                .map(employee -> PositionType.isCEO(
                        PositionType.getType(employee.getPosition())))
                .orElse(false);
    }

    // 대표이사와 일정 권한을 가진 관리자의 상세 조회 권한을 확인한다
    public boolean canViewAllLeaveDetails(Long employeeId) {
        if (employeeId == null) {
            return false;
        }

        return employeeRepository.findById(employeeId)
                .filter(employee -> employee.isActive(LocalDate.now(clock)))
                .map(employee -> {
                    PositionType position = PositionType.getType(employee.getPosition());
                    return PositionType.isCEO(position)
                            || (PositionType.isDirectorOrAbove(position)
                                    && teamService.isTeamManagerFromDatabase(employeeId));
                })
                .orElse(false);
    }

    // 현재 재직 중인 직원의 인사권 보유 여부를 확인한다
    public boolean hasPersonnelAuthority(Long employeeId) {
        if (employeeId == null) {
            return false;
        }

        return employeeRepository.findById(employeeId)
                .filter(employee -> employee.isActive(LocalDate.now(clock)))
                .map(Employee::hasPersonnelAuthority)
                .orElse(false);
    }

    public void requireAuthenticatedAdmin(Long employeeId) {
        if (employeeId == null || !teamService.isTeamManagerFromDatabase(employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "인가되지 않은 사용자입니다. 다시 로그인해주세요.");
        }
    }

    public void requireAuthenticatedPersonnelAuthority(boolean personnelAuthority) {
        if (!personnelAuthority) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "인사권을 가진 관리자가 아닙니다.");
        }
    }

    public void requireAdmin(Long employeeId) {
        Employee employee = requireEmployee(employeeId);
        if (!employee.isActive(LocalDate.now(clock))
                || !teamService.isTeamManagerFromDatabase(employeeId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "인가되지 않은 사용자입니다. 다시 로그인해주세요.");
        }
    }

    public void requirePersonnelAuthority(Long employeeId) {
        Employee employee = requireEmployee(employeeId);
        if (!employee.isActive(LocalDate.now(clock)) || !employee.hasPersonnelAuthority()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "인사권을 가진 관리자가 아닙니다.");
        }
    }

    private Employee requireEmployee(Long employeeId) {
        if (employeeId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 직원입니다."));
    }
}
