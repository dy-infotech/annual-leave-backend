package com.dyinfotech.annualleavebackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SignUpDto {

    @Getter
    @NoArgsConstructor
    public static class SignUpRequest {

        @NotBlank(message = "사번을 입력해 주세요.")
        private String employeeNumber;

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        private String password;
    }

    @Getter
    @Builder
    public static class SignUpResponse {

        private Long employeeId;
        private String name;
        private String loginId;
    }
}
