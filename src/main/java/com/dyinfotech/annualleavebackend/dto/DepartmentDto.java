package com.dyinfotech.annualleavebackend.dto;

import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
        @Size(max = 50, message = "부서명은 50자 이하여야 합니다.")
        private String departmentName;
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        @NotBlank(message = "부서명은 필수입니다.")
        @Size(max = 50, message = "부서명은 50자 이하여야 합니다.")
        private String departmentName;
    }

    @Getter
    @Builder
    public static class DepartmentResponse {
        private Long departmentId;
        private String departmentName;
        private Boolean enabled;

        public static DepartmentResponse from(DepartmentCacheRow department) {
            return DepartmentResponse.builder()
                    .departmentId(department.departmentId())
                    .departmentName(department.departmentName())
                    .enabled(department.enabled())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class CreateResponse {
        private Long departmentId;
    }
}
