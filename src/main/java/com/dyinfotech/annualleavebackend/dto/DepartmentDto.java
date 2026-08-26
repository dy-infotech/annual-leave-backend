package com.dyinfotech.annualleavebackend.dto;

import com.dyinfotech.annualleavebackend.domain.Department;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DepartmentDto {

    @Getter
    @NoArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "부서명은 필수입니다.")
        private String departmentName;
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        @NotBlank(message = "부서명은 필수입니다.")
        private String departmentName;
    }

    @Getter
    @Builder
    public static class DepartmentResponse {
        private Long departmentId;
        private String departmentName;
        private Boolean enabled;

        public static DepartmentResponse from(Department department) {
            return DepartmentResponse.builder()
                    .departmentId(department.getDepartmentId())
                    .departmentName(department.getDepartmentName())
                    .enabled(department.getEnabled())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class CreateResponse {
        private Long departmentId;
    }

}
