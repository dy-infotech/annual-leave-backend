package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.dyinfotech.annualleavebackend.common.type.DepartmentType;
import com.dyinfotech.annualleavebackend.common.type.ManageType;
import com.dyinfotech.annualleavebackend.common.type.PositionType;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.support.CreatedAudit;
import com.dyinfotech.annualleavebackend.domain.support.HasCreatedAudit;
import com.dyinfotech.annualleavebackend.domain.support.HasUpdatedAudit;
import com.dyinfotech.annualleavebackend.domain.support.IpEntityListener;
import com.dyinfotech.annualleavebackend.domain.support.UpdatedAudit;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "employee")
@Getter
@EntityListeners({AuditingEntityListener.class, IpEntityListener.class})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employee implements HasCreatedAudit, HasUpdatedAudit {

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

    @Column(name = "accessed_ip", length = 45)
    private String accessedIp;

    @Column(name = "accessed_at")
    private LocalDateTime accessedAt;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "position", length = 50)
    private String position;

    @Column(name = "email", length = 100)
    private String email;

    @Transient
    private Role role = Role.EMPLOYEE;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private Employee approver;

    @Embedded
    private CreatedAudit createdAudit = new CreatedAudit();

    @Embedded
    private UpdatedAudit updatedAudit = new UpdatedAudit();

    @Builder
    public Employee(String employeeNumber, String name, Department department, Team team, String position,
            String email, Role role, String currYear, Float currTotalLeaveDays, LocalDate hireDate,
            LocalDate fireDate, Employee approver) {
        this.employeeNumber = employeeNumber;
        this.name = name;
        this.department = department;
        this.team = team;
        this.position = position;
        this.role = role != null ? role : Role.EMPLOYEE;
        this.currYear = currYear;
        this.currTotalLeaveDays = currTotalLeaveDays;
        this.hireDate = hireDate;
        this.fireDate = fireDate;
        this.approver = approver;
        changeEmail(email);
    }

    public void setEmployeeNumber(String employeeNumber) { this.employeeNumber = employeeNumber; }
    public void completeSignUp(String encodedPassword) { changePassword(encodedPassword); }
    public void changePassword(String encodedPassword) { this.password = encodedPassword; }
    public void changeApprover(Employee approver) { this.approver = approver; }
    public void changeDepartment(Department department) { this.department = department; }

    public void increaseAccessCount(LocalDateTime now) {
        ++this.accessCount;
        this.accessedAt = now;
    }

    public void initAccessCount(LocalDateTime now) {
        this.accessCount = 0;
        this.accessedAt = now;
    }

    public void changeEmail(String email) { this.email = email; }

    public void setCurrYear(String year) { this.currYear = year; }
    public void setCurrYearLeaveDays(Float leaveDays) { this.currTotalLeaveDays = leaveDays; }
    public void setPrevYear(String year) { this.prevYear = year; }
    public void setPrevYearLeaveDays(Float leaveDays) { this.prevTotalLeaveDays = leaveDays; }

    public Long getApproverId() { return approver != null ? approver.getEmployeeId() : null; }
    public Long getDepartmentId() { return department != null ? department.getDepartmentId() : null; }
    public String getDepartmentName() { return department != null ? department.getDepartmentName() : null; }
    public Long getTeamId() { return team != null ? team.getTeamId() : null; }
    public String getTeamName() { return team != null ? team.getTeamName() : null; }

    public boolean isRegisted() { return this.password != null && !this.password.isBlank(); }

    public boolean isActive(LocalDate now) {
        return this.fireDate == null || !this.fireDate.isBefore(now);
    }

    public boolean hasPersonnelAuthority() {
        return PositionType.isCEO(PositionType.getType(this.position));
    }

    public int getManageTypeByDepartmentAndPosition(Department requestedDepartment, PositionType position) {
        int manageType = 0;
        DepartmentType parent = DepartmentType.getParentDepartmentType();
        PositionType myPosition = PositionType.getType(this.position);

        if (PositionType.isCEO(myPosition)) {
            manageType = ManageType.IS_VALID_DEPARTMENT.addFlag(manageType);
        } else if (parent.equals(DepartmentType.getType(requestedDepartment.getDepartmentName()))) {
            // 대표이사 부서에는 대표이사만 등록할 수 있다.
        } else if (this.department.equals(requestedDepartment)) {
            manageType = ManageType.IS_VALID_DEPARTMENT.addFlag(manageType);
        }

        if (myPosition != null && position != null && myPosition.compareTo(position) > 0) {
            manageType = ManageType.IS_VALID_POSITION.addFlag(manageType);
        }

        return manageType;
    }

    public void updateInfoByAdmin(String name, String email, Department department, Team team, String position,
            LocalDate hireDate, LocalDate fireDate, Float currTotalLeaveDays) {
        this.name = name;
        this.department = department;
        this.team = team;
        this.position = position;
        this.hireDate = hireDate;
        this.fireDate = fireDate;
        this.currTotalLeaveDays = currTotalLeaveDays;
        changeEmail(email);
    }

    public void changeRole(Role role) { this.role = role; }
}
