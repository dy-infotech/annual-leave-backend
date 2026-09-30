package com.dyinfotech.annualleavebackend.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * /api/admin/** 기본 관리자 권한 대신 현재 인사권 검증이 필요한 핸들러를 표시한다.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePersonnelAuthority {
}
