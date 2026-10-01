package com.dyinfotech.annualleavebackend.dto;

import com.dyinfotech.annualleavebackend.common.type.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
        @Size(max = 20, message = "사번은 20자 이하여야 합니다.")
        private String employeeNumber;
        
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        private String name;

        @NotBlank(message = "부서를 입력해 주세요.")
        @Size(max = 50, message = "부서명은 50자 이하여야 합니다.")
        private String department;

        @NotBlank(message = "팀을 입력해 주세요.")
        @Size(max = 30, message = "팀명은 30자 이하여야 합니다.")
        private String team;

        @NotBlank(message = "직급을 입력해 주세요.")
        @Size(max = 50, message = "직급은 50자 이하여야 합니다.")
        private String position;

        @Email(message = "유효하지 않은 이메일 형식입니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        private String email;
        private Role role;

        @NotBlank(message = "입사일을 입력해 주세요.")
        @Size(max = 10, message = "입사일 형식이 올바르지 않습니다.")
        private String hireDate;
    }

    @Getter
    @Builder
    public static class RegisterResponse {
        private Long employeeId;
        private String employeeNumber;
    }

}
