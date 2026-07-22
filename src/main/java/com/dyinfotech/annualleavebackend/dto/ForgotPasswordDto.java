package com.dyinfotech.annualleavebackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
 

public class ForgotPasswordDto { 
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FindIdRequest {
        @NotBlank(message = "이름을 입력해 주세요.")
        private String name;
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "유효하지 않은 이메일 형식입니다.")
        private String email;
    }

//    @Getter
//    @AllArgsConstructor
//    public static class FindIdResponse {
//        private String employeeNumber;
//    }
      
    @Getter
    @NoArgsConstructor
    public static class Request {

        @NotBlank(message = "사번을 입력해 주세요.")
        private String employeeNumber;
 
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "유효하지 않은 이메일 형식입니다.")
        private String email;
    } 
    
}
