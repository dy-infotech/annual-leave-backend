package com.dyinfotech.annualleavebackend.common.exception;

import java.time.format.DateTimeParseException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
 
    // 1) 기존 코드가 던지는 ResponseStatusException → status/reason 그대로 통일 포맷으로
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        // reason은 우리가 넣은 메시지라 노출 OK
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status, e.getReason(), req.getRequestURI()));
    }
 
    // 2) @Valid 검증 실패 → 첫 번째 필드 에러 메시지로 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = (fe != null) ? fe.getField() + ": " + fe.getDefaultMessage() : "요청 값이 올바르지 않습니다.";
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, msg, req.getRequestURI()));
    }
 
    // 3) 잘못된 바디/타입(잘못된 날짜 문자열 등) → 400 (내부 메시지 숨김)
    @ExceptionHandler({ HttpMessageNotReadableException.class, DateTimeParseException.class })
    public ResponseEntity<ErrorResponse> handleBadInput(Exception e, HttpServletRequest req) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.", req.getRequestURI()));
    }
 
    // 4) 그 외 미처리 예외 → 500, 상세는 로그로만, 응답은 일반 메시지
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest req) {
        log.error("처리되지 않은 예외 발생 [{}]", req.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.", req.getRequestURI()));
    }
}
