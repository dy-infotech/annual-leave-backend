package com.dyinfotech.annualleavebackend.dto;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogoutDto {

    @Getter
    @NoArgsConstructor
    public static class LogoutRequest {

        @Size(max = 255, message = "FCM 토큰은 255자 이하여야 합니다.")
        private String fcmToken;
    }
}
