package com.dyinfotech.annualleavebackend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

public class LogoutDto {

    @Getter
    @NoArgsConstructor
    public static class LogoutRequest {

        //@NotBlank(message = "FCM 토큰을 입력해 주세요.")
        private String fcmToken;
    }
}
