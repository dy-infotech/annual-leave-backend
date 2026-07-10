package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.common.type.Sign;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Singular;

@Entity
@Table(name = "employee")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "employee_number", nullable = false, unique = true, length = 20)
    private String employeeNumber;

    @Column(name = "password")
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "department", length = 50)
    private String department;
    
    @Column(name = "team", nullable = false, length = 30)
    private String team;

    @Column(name = "position", length = 50)
    private String position;

    @Column(name = "email", length = 50)
    private String email;

//    @Column(name = "login_id", unique = true, length = 50)
//    private String loginId;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_manager_id")
    @Singular("addTeam")
    private List<Team> teamData;

//    @Enumerated(EnumType.STRING)
//    @Column(name = "role", nullable = false, length = 10)
    @Transient
    private Role role;
    
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private List<LeaveAdjustment> leaveAdjustments;

    @Transient
    private float adjustedLeaveDays;
    
    @Column(name = "curr_year", nullable = false, length = 4)
    private String currYear;

    @Column(name = "curr_total_leave_days", nullable = false)
    private Float currTotalLeaveDays;
    
    @Column(name = "prev_year", length = 4)
    private String prevYear;
    
    @Column(name = "prev_total_leave_days")
    private Float prevTotalLeaveDays;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;
    
    @Column(name = "fire_date")
    private LocalDate fireDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Employee(String employeeNumber, String name, String department, String team, String position, String email, String currYear, Float currTotalLeaveDays, LocalDate hireDate, Long approverId) {
        this.employeeNumber = employeeNumber;
        this.name = name;
        this.department = department;
        this.team = team;
        this.position = position;
        this.email = email;
        this.currYear = currYear;
        this.currTotalLeaveDays = currTotalLeaveDays;
        this.hireDate = hireDate;
        this.approverId = approverId;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    @PostLoad
    protected void initialize() {
        // 1. Role 설정: teamData 리스트에 값이 있으면 ADMIN, 없으면 EMPLOYEE
        if (this.teamData != null && !this.teamData.isEmpty()) {
            this.role = Role.ADMIN;
        } else {
            this.role = Role.EMPLOYEE;
        }
        
        // 2. 조정된 연차일수 계산: leaveAdjustments 리스트를 순회
        float adjustedDays = 0.0f;
        if (this.leaveAdjustments != null) {
            for (LeaveAdjustment adjustment : this.leaveAdjustments) {
                adjustedDays += Sign.plus.name().equals(adjustment.getSign()) ? adjustment.getLeaveDays() : -adjustment.getLeaveDays();
            }
        }
        this.adjustedLeaveDays = adjustedDays;
        
        // 3. 현재 연도 연차일수는 Service에서 계산하여 setter를 통해 설정됨
        // @PostLoad에서는 계산하지 않음 (BasisDataFactory는 Service에서만 접근 가능)
    }
    
    
    public void setEmployeeNumber(String employeeNumber) {
		this.employeeNumber = employeeNumber;
	}
    
    // 회원가입 완료 처리 (비밀번호 설정)
    public void completeSignUp(String encodedPassword) {
        changePassword(encodedPassword);
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
    
    public void setRole(Role role) {
    	this.role = role;
    }

    public void setCurrYear(String year) {
        this.currYear = year;
    }

    public void setCurrYearLeaveDays(Float leaveDays) {
        this.currTotalLeaveDays = leaveDays;
    }
    
    public void setPrevYear(String year) {
        this.prevYear = year;
    }

    public void setPrevYearLeaveDays(Float leaveDays) {
        this.prevTotalLeaveDays = leaveDays;
    }

}