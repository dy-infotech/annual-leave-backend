package com.dyinfotech.annualleavebackend.service;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeLeaveService employeeLeaveService;

    public EmployeeDto.EmployResponse getMyInfo(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        // 현재 연도 연차일수 계산 및 설정
        employeeLeaveService.calculateAndSetCurrentYearLeaveDays(employee);

        return EmployeeDto.EmployResponse.builder()
                .employeeNumber(employee.getEmployeeNumber())
                .name(employee.getName())
                .position(employee.getPosition())
                .department(employee.getDepartment())
                .build();
    }

    @Transactional
    public void changePassword(Long employeeId, EmployeeDto.PasswordChangeRequest request) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), employee.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }

        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        employee.changePassword(encodedNewPassword);
    }
}