package com.dyinfotech.annualleavebackend.common.exception;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PROTECTED)
public class ErrorResponse {
    private final LocalDateTime timestamp;
    private final int status;        // HTTP status code (403 등)
    private final String error;      // "FORBIDDEN" 같은 상태 문구
    private final String message;    // 사용자에게 보일 메시지
    private final String path;       // 요청 URI
}