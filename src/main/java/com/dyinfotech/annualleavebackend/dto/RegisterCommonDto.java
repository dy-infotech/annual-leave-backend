package com.dyinfotech.annualleavebackend.dto;

import java.util.Collection;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class RegisterCommonDto {

    @Getter
    @NoArgsConstructor
    public static class RegisterCommonRequest {
    	
    }

    @Getter
    @Builder
    public static class RegisterCommonResponse {
        private Collection<String> department;	// 부서
        private Collection<String> team;		// 팀
        private Collection<String> position;	// 직급
    }

}
