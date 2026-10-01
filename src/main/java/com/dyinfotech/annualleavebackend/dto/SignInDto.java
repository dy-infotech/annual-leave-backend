package com.dyinfotech.annualleavebackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SignInDto {

    @Getter
    @NoArgsConstructor
    public static class SignInRequest {
        @NotBlank(message = "사번을 입력해 주세요.")
        @Size(max = 20, message = "사번은 20자 이하여야 합니다.")
        private String employeeNumber;

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(max = 72, message = "비밀번호는 72자 이하여야 합니다.")
        private String password;
    }

    @Getter
    @Builder
    public static class SignInResponse {

        private String token;
        private Long employeeId;
        private String name;
        private String role;
        private String email;
        private String ssoSessionMarker;
    }
}
