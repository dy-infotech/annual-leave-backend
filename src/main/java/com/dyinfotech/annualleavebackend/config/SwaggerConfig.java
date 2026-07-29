package com.dyinfotech.annualleavebackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI annualLeaveOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("근태 관리 API")
                        .description("연차 신청 · 승인 · 조회 API 문서")
                        .version("v1.0.0")
                        .contact(new Contact()
                            .name("디와이정보기술")
                            .email("dywoo@naver.com")));
    }
}