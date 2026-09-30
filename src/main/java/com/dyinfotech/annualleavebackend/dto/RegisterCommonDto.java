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
        private Collection<String> department;        // 부서
        private Collection<String> accessibleTeam;    // 하위 호환용 팀명 목록
        private Collection<TeamOptionResponse> accessibleTeamInfo; // 팀-부서 관계 포함
        private Collection<String> position;          // 직급
    }

    @Getter
    @Builder
    public static class TeamOptionResponse {
        private Long teamId;
        private String teamName;
        private Long departmentId;
        private String departmentName;
    }

}
