package com.dyinfotech.annualleavebackend.dto;

import java.util.Collection;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
 
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FindDataDto { 
	@Getter
    @NoArgsConstructor
	public static class FindEmailByIdRequest {
        @NotBlank(message = "이름을 입력해 주세요.")
		private String name;
	}
	
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FindIdRequest {
        @NotBlank(message = "이름을 입력해 주세요.")
        private String name;
        @NotBlank(message = "이메일을 입력해 주세요.")
        private String email;
    }
    
	@Getter
    @NoArgsConstructor
	public static class FindEmailByEmployeeNumberRequest {
        @NotBlank(message = "사번을 입력해 주세요.")
        private String employeeNumber;
	}
    
    @Getter
    @NoArgsConstructor
    public static class FindPasswordRequest {
        @NotBlank(message = "사번을 입력해 주세요.")
        private String employeeNumber;
 
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        private String email;
    }
    
    @Getter
    @Builder
    public static class EmailResponse {
    	private Collection<String> maskedEmailList;
    }
    
}
