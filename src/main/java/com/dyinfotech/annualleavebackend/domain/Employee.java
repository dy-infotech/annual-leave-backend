package com.dyinfotech.annualleavebackend.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.dyinfotech.annualleavebackend.common.type.DepartmentType;
import com.dyinfotech.annualleavebackend.common.type.ManageType;
import com.dyinfotech.annualleavebackend.common.type.PositionType;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
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
import jakarta.persistence.OneToMany;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;
    
    @OneToMany(mappedBy = "projectManager")
    private List<TeamManager> teams = new ArrayList<>();

    @Column(name = "position", length = 50)
    private String position;

    @Column(name = "email", length = 100)
    private String email;
    
    // 💡 [수정] @Transient를 붙여 실제 MySQL DB 테이블을 변경하지 않고 메모리 상에서만 활용하도록 차단막 설정
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
    public Employee(String employeeNumber, String name, Department department, Team team, String position, String email, Role role, String currYear, Float currTotalLeaveDays, LocalDate hireDate, LocalDate fireDate, Employee approver) {
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

    public void changeApprover(Employee approver) {
        this.approver = approver;
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
    	
    	// 이름은 중복될 수 있으므로 아예 만료시킨다
    	CacheConfig.EMAIL_BY_NAME_CACHE.invalidate(name);
    	// 사번은 중복될 수 없으므로 업데이트한다
    	CacheConfig.EMAIL_BY_EMPLOYEE_NUMBER_CACHE.put(employeeNumber, email);
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
    
    public Long getApproverId() {
        return approver != null ? approver.getEmployeeId() : null;
    }
    
    public boolean isRegisted() {
    	return this.password != null && !this.password.isBlank();
    }
    
    public boolean hasPersonnelAuthority() {
    	// 사장만 인사권을 가지고 있으며, 관리자(PM)를 등록할 수 있음
    	return PositionType.isCEO(PositionType.getType(this.position));
    }
    
    public int getManageTypeByDepartmentAndPosition(Department requestedDepartment, PositionType position) {
    	int manageType = 0;
    	DepartmentType department = DepartmentType.getType(this.department.getDepartmentName());
    	DepartmentType parent = DepartmentType.getParentDepartmentType();
    	PositionType myPosition = PositionType.getType(this.position);
    	// 대표이사 부서에 등록할 경우
    	if (parent.equals(DepartmentType.getType(requestedDepartment.getDepartmentName()))) {
    		// 대표이사 부서의 대표이사만 등록 가능
    		if (parent.equals(department) && PositionType.isCEO(myPosition)) {
    			manageType = ManageType.IS_VALID_DEPARTMENT.addFlag(manageType);
    		}
    	} else if (this.department.equals(requestedDepartment)) {
        	// 대표이사 부서가 아니면 같은 부서일 때만 등록 가능
    		manageType = ManageType.IS_VALID_DEPARTMENT.addFlag(manageType);
    	}
    	
    	// 나보다 낮은 직급일 때 등록 가능
    	if (myPosition != null && myPosition.compareTo(position) > 0) {
    		manageType = ManageType.IS_VALID_POSITION.addFlag(manageType);
    	}
    	
    	return manageType;
    } 
    // 관리자용 사원 정보 수정 메서드
    public void updateInfoByAdmin(
        String name, 
        String email, 
        Department department, 
        Team team, 
        String position, 
        LocalDate hireDate, 
        LocalDate fireDate,
        Float currTotalLeaveDays
    ) {
        this.name = name;
        this.department = department;
        this.team = team;
        this.position = position;
        this.hireDate = hireDate;     
        this.fireDate = fireDate;    
        this.currTotalLeaveDays = currTotalLeaveDays;
        
        changeEmail(email);
    }

 
    public void changeRole(Role role) {
        this.role = role; 
    }
}
