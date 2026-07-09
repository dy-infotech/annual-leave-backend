package com.dyinfotech.annualleavebackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class SignInDto {

    @Getter
    @NoArgsConstructor
    public static class SignInRequest {

        @NotBlank(message = "사번을 입력해 주세요.")
        private String employeeNumber;

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        private String password;
    }

    @Getter
    @Builder
    public static class SignInResponse {

        private String token;
        private Long employeeId;
        private String name;
        private String role;
    }
}
