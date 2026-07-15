package com.dyinfotech.annualleavebackend.dto;

import java.time.LocalDate;

import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.Employee;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class EmployeeDto {

    @Getter
    @NoArgsConstructor
    public static class PasswordChangeRequest {

        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        private String currentPassword;

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        private String newPassword;
    }

    @Getter
    @Builder
    public static class EmployResponse {

        private String employeeNumber;
        private String name;
        private String department;
        private String position;
        private String email;
        private LocalDate hireDate;
        private String role;
        private Float currTotalLeaveDays;   // 이번 연도 총 연차 일수
        private Float remainingLeaveDays;   // 남은 연차 일수

        public static EmployResponse from(Employee employee, Role role, Float remainingLeaveDays) {
            return EmployResponse.builder()
                    .employeeNumber(employee.getEmployeeNumber())
                    .name(employee.getName())
                    .department(employee.getDepartment())
                    .position(employee.getPosition())
                    .email(employee.getEmail())
                    .hireDate(employee.getHireDate())
                    .role(role.name())
                    .currTotalLeaveDays(employee.getCurrTotalLeaveDays())
                    .remainingLeaveDays(remainingLeaveDays)
                    .build();
        }
    }
}
