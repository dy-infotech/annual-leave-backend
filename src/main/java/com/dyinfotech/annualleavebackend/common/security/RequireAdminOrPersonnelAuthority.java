package com.dyinfotech.annualleavebackend.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 현재 PM 관리자 또는 현재 인사권자 중 하나이면 접근 가능한 /api/admin/** 핸들러.
 *
 * 직원 등록/조회처럼 일반 PM의 관리 범위 업무와 인사권자의 전사 업무가
 * 같은 진입점을 공유하는 경우에 사용한다.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdminOrPersonnelAuthority {
}
