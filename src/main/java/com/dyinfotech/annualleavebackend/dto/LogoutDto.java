package com.dyinfotech.annualleavebackend.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogoutDto {

    @Getter
    @NoArgsConstructor
    public static class LogoutRequest {

        //@NotBlank(message = "FCM 토큰을 입력해 주세요.")
        private String fcmToken;
    }
}
