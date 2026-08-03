package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.dyinfotech.annualleavebackend.domain.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "employee")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employee extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "employee_number", nullable = false, unique = true, length = 20)
    private String employeeNumber;

    @Column(name = "password")
    private String password;
    
    @Column(name = "access_count", nullable = false)
    private Integer accessCount = 0;
    
    @Column(name = "accessed_at")
    private LocalDateTime accessedAt;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "department", length = 50)
    private String department;
    
    @Column(name = "team", nullable = false, length = 30)
    private String team;
    
    @OneToMany(mappedBy = "projectManager")
    private List<Team> teams = new ArrayList<>();

    @Column(name = "position", length = 50)
    private String position;

    @Column(name = "email", length = 100)
    private String email;
    
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
    
    @Column(name = "approver_id", nullable = false)
    private Long approverId;

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

    public void changeApprover(Long approverId) {
        this.approverId = approverId;
    }
    
    public void increaseAccessCount(LocalDateTime now) {
    	++this.accessCount;
    	this.accessedAt = now;
    }
    
    public void initAccessCount(LocalDateTime now) {
    	this.accessCount = 0;
    	this.accessedAt = now;
    }
    
    public void changeEmail(String email) {
    	this.email = email;
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
    
    public boolean isRegisted() {
    	return this.password != null && !this.password.isBlank();
    }
    
    // 관리자용 사원 정보 수정 메서드
    public void updateInfoByAdmin(String name, String email, String department, String team, String position, LocalDate hireDate, Float currTotalLeaveDays) {
        this.name = name;
        this.email = email;             
        this.department = department;
        this.team = team;
        this.position = position;
        this.hireDate = hireDate;        
        this.currTotalLeaveDays = currTotalLeaveDays;
    }
}