package com.dyinfotech.annualleavebackend.dto;

import java.util.Collection;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
		private String name;
	}
	
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FindIdRequest {
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        private String name;
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        private String email;
    }
    
	@Getter
    @NoArgsConstructor
	public static class FindEmailByEmployeeNumberRequest {
        @NotBlank(message = "사번을 입력해 주세요.")
        @Size(max = 20, message = "사번은 20자 이하여야 합니다.")
        private String employeeNumber;
	}
    
    @Getter
    @NoArgsConstructor
    public static class FindPasswordRequest {
        @NotBlank(message = "사번을 입력해 주세요.")
        @Size(max = 20, message = "사번은 20자 이하여야 합니다.")
        private String employeeNumber;
 
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        private String email;
    }
    
    @Getter
    @NoArgsConstructor
    public static class ResetPasswordRequest {
        @NotBlank(message = "재설정 토큰을 입력해 주세요.")
        @Size(max = 128, message = "재설정 토큰 형식이 올바르지 않습니다.")
        private String token;

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Size(max = 72, message = "새 비밀번호는 72자 이하여야 합니다.")
        private String newPassword;
    }

    @Getter
    @Builder
    public static class EmailResponse {
    	private Collection<String> maskedEmailList;
    }
    
}
