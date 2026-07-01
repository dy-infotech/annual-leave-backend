package com.dyinfotech.annualleavebackend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "department", length = 50)
    private String department;

    @Column(name = "position", length = 50)
    private String position;

    @Column(name = "login_id", unique = true, length = 50)
    private String loginId;

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    @Column(name = "total_leave_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal totalLeaveDays;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Employee(String employeeNo, String name, String department, String position,
                    Role role, BigDecimal totalLeaveDays, LocalDate hireDate) {
        this.employeeNo = employeeNo;
        this.name = name;
        this.department = department;
        this.position = position;
        this.role = role;
        this.totalLeaveDays = totalLeaveDays;
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

    // 회원가입 완료 처리 (로그인 아이디 = 사번, 비밀번호 설정)
    public void completeSignUp(String loginId, String encodedPassword) {
        this.loginId = loginId;
        this.password = encodedPassword;
    }
}