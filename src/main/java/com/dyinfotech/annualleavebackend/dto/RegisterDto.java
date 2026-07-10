package com.dyinfotech.annualleavebackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class RegisterDto {

    @Getter
    @NoArgsConstructor
    public static class RegisterRequest {

        @NotBlank(message = "이름을 입력해 주세요.")
        private String name;

        private String department;
        @NotBlank(message = "팀을 입력해 주세요.")
        private String team;
        private String position;
        private String email;

        @NotBlank(message = "입사일을 입력해 주세요.")
        private String hireDate;
        
        @NotBlank(message = "승인자 번호를 입력해 주세요.")
        private Long approverId;
    }

    @Getter
    @Builder
    public static class RegisterResponse {
        private Long employeeId;
        private String loginId;		// equals to employeeNumber
    }

}
