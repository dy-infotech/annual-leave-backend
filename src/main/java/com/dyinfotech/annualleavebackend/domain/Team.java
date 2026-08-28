package com.dyinfotech.annualleavebackend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "team")
@Getter
@EqualsAndHashCode(of = "teamId")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "team_name", length = 30, nullable = false)
    private String teamName;

    // 부서:팀 = 1:N 소속. 팀 캐시에 detached 상태로 저장되므로 EAGER로 즉시 로딩한다 (LazyInitializationException 방지)
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Builder
    public Team(String teamName, Boolean enabled, Department department) {
        this.teamName = teamName;
        this.enabled = enabled;
        this.department = department;
    }
    
    public void changeName(String teamName) {
        this.teamName = teamName;
    }
    
    public void changeDepartment(Department department) {
        this.department = department;
    }
    
    public void disable() {
        this.enabled = false;
    }
}
