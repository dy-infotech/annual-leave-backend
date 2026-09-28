package com.dyinfotech.annualleavebackend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.service.TeamService.ManagedTeam;
import com.dyinfotech.annualleavebackend.service.EmployeeLeaveService.EmployeeAuthorityResolver;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmployeeDto {

    @Getter
    @NoArgsConstructor
    public static class ModifyEmailRequest {
    	@NotBlank(message = "이메일은 필수입니다.")
	    @Email(message = "유효하지 않은 이메일 형식입니다.")
        private String email;
    }

    @Getter
    @NoArgsConstructor
    public static class PasswordChangeRequest {

        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        private String currentPassword;

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        private String newPassword;
    }

    @Getter
    @Builder
    public static class EmployeeResponse {

        private Long employeeId;
        private String employeeNumber;
        private String name;
        private String department;
        private String team;
        private List<String> teamList;
        private String position;
        private String email;
        private LocalDate hireDate;
        private LocalDate fireDate;
        @Deprecated
        private String role;
        private Float currTotalLeaveDays;   // 이번 연도 총 연차 일수
        private Float remainingLeaveDays;   // 남은 연차 일수
        private String approverNumber;
        private String approverName;
        private String approverPosition;
        private String approverDepartment;
        private LocalDateTime createdAt;
        private Boolean isRegisted;

        public static EmployeeResponse from(Employee employee, Employee approver, EmployeeAuthorityResolver authorityResolver, Float remainingLeaveDays) {
            return from(employee, approver, authorityResolver, employee.getCurrTotalLeaveDays(), remainingLeaveDays);
        }

        public static EmployeeResponse from(Employee employee, Employee approver, EmployeeAuthorityResolver authorityResolver, Float currTotalLeaveDays, Float remainingLeaveDays) {
            return EmployeeResponse.builder()
                    .employeeId(employee.getEmployeeId())
                    .employeeNumber(employee.getEmployeeNumber())
                    .name(employee.getName())
                    .department(employee.getDepartmentName())
                    .team(employee.getTeamName())
                    .teamList(authorityResolver.getManagedTeams(employee.getEmployeeId()).stream().map(ManagedTeam::teamName).toList())
                    .position(employee.getPosition())
                    .email(employee.getEmail())
                    .hireDate(employee.getHireDate())
                    .fireDate(employee.getFireDate())
                    .role(authorityResolver.resolveRole(employee.getEmployeeId()).name())
                    .currTotalLeaveDays(currTotalLeaveDays)
                    .remainingLeaveDays(remainingLeaveDays)
                    .approverNumber(approver.getEmployeeNumber())
                    .approverName(approver.getName())
                    .approverPosition(approver.getPosition())
                    .approverDepartment(approver.getDepartmentName())
                    .isRegisted(employee.isRegisted())
                    .createdAt(employee.getCreatedAudit().getCreatedAt())
                    .build();
        }
    }
    
    @Getter
    @NoArgsConstructor
    public static class EmployeeAdminUpdateRequest {
        @NotBlank(message = "이름은 필수입니다.")
        private String name;

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "유효하지 않은 이메일 형식입니다.")
        private String email;

        @NotBlank(message = "부서는 필수입니다.")
        private String department;

        @NotNull(message = "입사일은 필수입니다.")
        private LocalDate hireDate; 
        private LocalDate fireDate;
        
        private String team;
        
        /**
         * 최종 관리팀 집합. null이면 기존 targetTeamsForRoleSwap 토글 방식을 사용한다.
         * 동일 요청 재전송에 안전한 멱등 경로다.
         */
        private Collection<String> managedTeams;

        /** 하위 호환용 토글 목록. managedTeams가 전달되면 무시한다. */
        private Collection<String> targetTeamsForRoleSwap;

        private String position;
    }
    
}

