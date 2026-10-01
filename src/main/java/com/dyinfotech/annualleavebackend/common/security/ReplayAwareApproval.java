package com.dyinfotech.annualleavebackend.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 승인/반려 write API의 멱등 재전송 계약을 표시한다.
 *
 * 인증 자체는 기존 Security filter에서 유지하고, 현재 관리자 여부의 선차단만 생략한다.
 * 서비스가 조직 mutex 아래에서 동일 결과 재전송을 먼저 판정하고,
 * 실제 상태 변경이 필요한 경우 최신 DB 조직 기준 결재권을 검증한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReplayAwareApproval {
}
