package com.dyinfotech.annualleavebackend.dto;

import com.dyinfotech.annualleavebackend.common.type.Role;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RegisterDto {

    @Getter
    @NoArgsConstructor
    public static class RegisterRequest {
    	// 260810 추가
        @NotBlank(message = "사번을 입력해 주세요.")
        private String employeeNumber;
        
        @NotBlank(message = "이름을 입력해 주세요.")
        private String name;

        private String department;
        @NotBlank(message = "팀을 입력해 주세요.")
        private String team;
        private String position;
        private String email;
        private Role role;

        @NotBlank(message = "입사일을 입력해 주세요.")
        private String hireDate;
    }

    @Getter
    @Builder
    public static class RegisterResponse {
        private Long employeeId;
        private String employeeNumber;
    }

}
