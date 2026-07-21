package com.dyinfotech.annualleavebackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
 

public class ForgotPasswordDto { 
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FindIdRequest {
        private String name;
        private String email;
    }

    /**
     * 💡 아이디 찾기 결과 응답 (Response)
     * 서버에서 찾은 사원번호(employeeNumber)를 플러터로 돌려줄 때 사용합니다.
     */
    @Getter
    @AllArgsConstructor
    public static class FindIdResponse {
        private String employeeNumber;
    }
      
    @Getter
    @NoArgsConstructor
    public static class Request {

        @NotBlank(message = "사번을 입력해 주세요.")
        private String employeeNumber;
 
        @NotBlank(message = "이메일을 입력해 주세요.")
        private String email;
    } 
    
}
