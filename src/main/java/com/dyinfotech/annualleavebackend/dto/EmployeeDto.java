package com.dyinfotech.annualleavebackend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

        private String employeeNumber;
        private String name;
        private String department;
        private String team; // 💡 1. 팀(Team) 필드 선언 추가
        private List<String> teamList;
        private String position;
        private String email;
        private LocalDate hireDate;
//        private String role;
        private Float currTotalLeaveDays;   // 이번 연도 총 연차 일수
        private Float remainingLeaveDays;   // 남은 연차 일수
        private String approverNumber;
        private String approverName;
        private String approverPosition;
        private String approverDepartment;
        private LocalDateTime createdAt;
        private Boolean isRegisted;

        public static EmployeeResponse from(Employee employee, Employee approver, Role role, Float remainingLeaveDays) {
            return EmployeeResponse.builder()
                    .employeeNumber(employee.getEmployeeNumber())
                    .name(employee.getName())
                    .department(employee.getDepartment())
                    .team(employee.getTeam())
                    .teamList(employee.getTeams().stream().map(Team::getTeam).toList())
                    .position(employee.getPosition())
                    .email(employee.getEmail())
                    .hireDate(employee.getHireDate())
//                    .role(role.name())
                    .currTotalLeaveDays(employee.getCurrTotalLeaveDays())
                    .remainingLeaveDays(remainingLeaveDays)
                    .approverNumber(approver.getEmployeeNumber())
                    .approverName(approver.getName())
                    .approverPosition(approver.getPosition())
                    .approverDepartment(approver.getDepartment())
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
        private String email; // 💡 플러터 이메일 컨트롤러 값 바인딩용 추가

        @NotBlank(message = "부서는 필수입니다.")
        private String department;

        // LocalDate 타입으로 직접 받거나, 포맷팅 문제가 염려된다면 String으로 받아 서비스 레이어에서 변환합니다.
        // 플러터 텍스트컨트롤러에서 yyyy-MM-dd 문자열로 넘어오므로 LocalDate가 안전하게 매핑됩니다.
        private String hireDate; // 💡 플러터 입사일 컨트롤러 값 바인딩용 추가

        // --- 이래 필드들은 플러터 상세 화면에서 수정 요청을 같이 보내지 않는다면 제외하거나, 
        // --- 프론트엔드 데이터 구조와 맞춰야 합니다. (선택사항)
        private String team;
        
        private String targetTeamForRoleSwap;

        private String position;

        private Long approverId; 

        private Float currTotalLeaveDays; 
    }
}
