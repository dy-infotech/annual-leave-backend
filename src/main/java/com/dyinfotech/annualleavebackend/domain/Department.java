package com.dyinfotech.annualleavebackend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "department")
@Getter
@EqualsAndHashCode(of = "departmentId")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "department_name", length = 30, nullable = false)
    private String departmentName;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Builder
    public Department(String departmentName, Boolean enabled) {
        this.departmentName = departmentName;
        this.enabled = enabled;
    }
    
    public void disable() {
        this.enabled = false;
    }
}
