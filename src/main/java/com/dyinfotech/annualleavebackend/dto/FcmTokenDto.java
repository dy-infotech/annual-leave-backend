package com.dyinfotech.annualleavebackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FcmTokenDto {

    @Getter
    @NoArgsConstructor
    public static class FcmTokenRequest {
        @NotBlank(message = "FCM 토큰을 입력해주세요.")
        @Size(max = 255, message = "FCM 토큰은 255자 이하여야 합니다.")
        private String fcmToken;
        
        @NotBlank(message = "디바이스 정보를 입력해주세요.")
        @Size(max = 255, message = "디바이스 정보는 255자 이하여야 합니다.")
        private String deviceOs;
    }

}