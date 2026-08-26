package com.dyinfotech.annualleavebackend.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TeamDto {

    @Getter
    @NoArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "팀명은 필수입니다.")
        private String teamName;

        @NotNull(message = "팀 담당자는 필수입니다.")
        private Long projectManagerId;

        // 미지정 시 요청자(대표이사)의 팀이 상위 팀으로 설정된다.
        private Long parentTeamId;
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        // null인 필드는 기존 값을 유지한다.
        private String teamName;
        // 지정 시 기존 담당자 전원이 새 담당자 1명으로 교체된다.
        private Long projectManagerId;
        private Long parentTeamId;
    }

    @Getter
    @Builder
    public static class TeamResponse {
        private Long teamId;
        private String teamName;
        private Boolean enabled;
        private Long parentTeamId;
        private String parentTeamName;
        private List<ManagerResponse> managers;
    }

    @Getter
    @Builder
    public static class ManagerResponse {
        private Long employeeId;
        private String employeeNumber;
        private String name;
        private String position;
    }

    @Getter
    @Builder
    public static class CreateResponse {
        private Long teamId;
    }

}
