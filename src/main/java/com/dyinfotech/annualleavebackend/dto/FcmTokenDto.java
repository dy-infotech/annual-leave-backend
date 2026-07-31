package com.dyinfotech.annualleavebackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FcmTokenDto {

    @Getter
    @NoArgsConstructor
    public static class FcmTokenRequest {
        @NotBlank(message = "FCM 토큰을 입력해주세요.")
        private String fcmToken;
        
        @NotBlank(message = "디바이스 정보를 입력해주세요.")
        private String deviceOs;
    }

}