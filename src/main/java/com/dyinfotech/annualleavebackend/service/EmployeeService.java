package com.dyinfotech.annualleavebackend.service;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.dto.EmployeeDto;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeDto.EmployResponse getMyInfo(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        return EmployeeDto.EmployResponse.builder()
                .employeeNo(employee.getEmployeeNo())
                .name(employee.getName())
                .position(employee.getPosition())
                .department(employee.getDepartment())
                .build();
    }
}