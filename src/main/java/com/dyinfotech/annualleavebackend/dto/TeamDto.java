package com.dyinfotech.annualleavebackend.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
        @Size(max = 30, message = "팀명은 30자 이하여야 합니다.")
        private String teamName;

        // 팀만 먼저 생성할 수 있으므로 선택값이다.
        private Long projectManagerId;

        @NotNull(message = "소속 부서는 필수입니다.")
        private Long departmentId;

        // 담당자를 함께 지정할 때만 사용한다.
        private Long parentTeamId;
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        @Size(max = 30, message = "팀명은 30자 이하여야 합니다.")
        private String teamName;
        private Long projectManagerId;
        private Long parentTeamId;
        private Long departmentId;
    }

    @Getter
    @Builder
    public static class TeamResponse {
        private Long teamId;
        private String teamName;
        private Boolean enabled;
        private Long departmentId;
        private String departmentName;
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
        private Boolean active;
    }

    @Getter
    @Builder
    public static class CreateResponse {
        private Long teamId;
    }
}
