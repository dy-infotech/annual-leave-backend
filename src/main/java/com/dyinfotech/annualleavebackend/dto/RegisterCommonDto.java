package com.dyinfotech.annualleavebackend.dto;

import java.util.Collection;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RegisterCommonDto {

    @Getter
    @NoArgsConstructor
    public static class RegisterCommonRequest {
    	
    }

    @Getter
    @Builder
    public static class RegisterCommonResponse {
        private Collection<String> department;		// 부서
        private Collection<String> accessibleTeam;	// 관리 대상 팀 (현재 관리자인 팀. 관리자에서 내려올 수도 있는 대상들)
        private Collection<String> position;		// 직급
    }

}
