package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
    private String employeeNo;

    @Column(name = "password")
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "department", length = 50)
    private String department;
    
    @Column(name = "team", length = 30)
    private String team;

    @Column(name = "position", length = 50)
    private String position;

//    @Column(name = "login_id", unique = true, length = 50)
//    private String loginId;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_manager_id")
    @Singular("team")
    private List<Team> teamData;

//    @Enumerated(EnumType.STRING)
//    @Column(name = "role", nullable = false, length = 10)
    private Role role;
    
    @Column(name = "curr_year", length = 4)
    private String currYear;

    @Column(name = "curr_total_leave_days", nullable = false)
    private Float currTotalLeaveDays;
    
    @Column(name = "prev_year", length = 4)
    private String prevYear;
    
    @Column(name = "prev_total_leave_days", nullable = false)
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
    public Employee(String employeeNo, String name, String department, String position,
                    String currYear, Float currTotalLeaveDays, LocalDate hireDate) {
        this.employeeNo = employeeNo;
        this.name = name;
        this.department = department;
        this.position = position;
        this.currYear = currYear;
        this.currTotalLeaveDays = currTotalLeaveDays;
        this.hireDate = hireDate;
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
    protected void initializeRole() {
        // teamData 리스트에 값이 있으면 ADMIN, 없으면 EMPLOYEE로 설정
        if (this.teamData != null && !this.teamData.isEmpty()) {
            this.role = Role.ADMIN;
        } else {
            this.role = Role.EMPLOYEE;
        }
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

}