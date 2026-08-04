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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "team",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_team_project_manager",
            columnNames = {"team", "project_manager_id"}
        )
    }
)
@Getter
@EqualsAndHashCode(of = "seq")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq")
    private Long seq;
	
    @Column(name = "team", length = 30)
    private String team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_manager_id", nullable = false)
    private Employee projectManager;
    
    @Column(name = "parent_team", length = 30)
    private String parentTeam;

    @Builder
    public Team(String team, Employee projectManager, String parentTeam) {
        this.team = team;
        this.projectManager = projectManager;
        this.parentTeam = parentTeam;
    }
    
    public Long getProjectManagerId() {
    	return projectManager != null ? projectManager.getEmployeeId() : null;
    }
}
