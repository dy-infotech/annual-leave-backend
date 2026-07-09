package com.dyinfotech.annualleavebackend.dto;

import com.dyinfotech.annualleavebackend.domain.Employee;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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
        private String position;
        private String department;
        private LocalDate hireDate;
        private String role;

        public static EmployResponse from(Employee employee) {
            return EmployResponse.builder()
                    .employeeNumber(employee.getEmployeeNumber())
                    .name(employee.getName())
                    .position(employee.getPosition())
                    .department(employee.getDepartment())
                    .hireDate(employee.getHireDate())
                    .role(employee.getRole().name())
                    .build();
        }
    }
}
