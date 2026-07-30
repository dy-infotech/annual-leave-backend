package com.dyinfotech.annualleavebackend.common.exception;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ErrorResponseFactory {

    private final Clock clock;

    public ErrorResponse create(
    		org.springframework.http.HttpStatus status,
            String message,
            String path
    ) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now(clock))
                .status(status.value())
                .error(status.name())
                .message(message)
                .path(path)
                .build();
    }
}
