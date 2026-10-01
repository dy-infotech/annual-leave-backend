package com.dyinfotech.annualleavebackend.controller;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포 readiness 확인용 경량 endpoint.
 *
 * 애플리케이션이 이 endpoint에 응답한다는 것은 Spring context 초기화와
 * JPA schema validation을 통과했다는 뜻이다. 민감정보나 DB 데이터를 노출하지 않는다.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("status", "UP"));
    }
}
