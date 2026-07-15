package com.dyinfotech.annualleavebackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
 

public class ForgotPasswordDto {

    @Getter
    @NoArgsConstructor
    public static class Request {

        @NotBlank(message = "사번을 입력해 주세요.")
        private String employeeNumber;
 
        @NotBlank(message = "이메일을 입력해 주세요.")
        private String email;
    } 
}
