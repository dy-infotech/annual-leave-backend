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
                    .approverNumber(approver != null ? approver.getEmployeeNumber() : null)
                    .approverName(approver != null ? approver.getName() : null)
                    .approverPosition(approver != null ? approver.getPosition() : null)
                    .approverDepartment(approver != null ? approver.getDepartmentName() : null)
                    .isRegisted(employee.isRegisted())
                    .createdAt(employee.getCreatedAudit().getCreatedAt())
                    .build();
        }
    }
    
    @Getter
    @NoArgsConstructor
    public static class ManagedTeamsUpdateRequest {
        /**
         * 화면이 마지막으로 조회했을 때의 관리팀 집합.
         * Backend가 현재 상태와 비교해 stale update를 409로 차단한다.
         */
        @NotNull(message = "기준 관리팀 목록은 필수입니다.")
        private Collection<String> expectedManagedTeams;

        /**
         * 저장 후 원하는 최종 관리팀 집합.
         * 동일 요청 재전송 시 current == desired이면 no-op 성공한다.
         */
        @NotNull(message = "최종 관리팀 목록은 필수입니다.")
        private Collection<String> managedTeams;
    }
    
    @Getter
    @NoArgsConstructor
    public static class EmployeeAdminExpectedState {
        private String name;
        private String email;
        private String department;
        private String team;
        private String position;
        private LocalDate hireDate;
        private LocalDate fireDate;
    }
    
    @Getter
    @NoArgsConstructor
    public static class EmployeeAdminUpdateRequest {
        /**
         * v2 화면이 마지막으로 조회한 인사정보 snapshot.
         * 값이 있으면 잠금 획득 후 현재 DB 상태와 비교해 stale update를 409로 차단한다.
         * 구 프론트 호환을 위해 생략 가능하다.
         */
        private EmployeeAdminExpectedState expected;

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

        private String position;
    }
    
}

