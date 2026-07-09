package com.dyinfotech.annualleavebackend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "team")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq")
    private Long seq;
	
    @Column(name = "team", length = 30)
    private String team;

    @Column(name = "project_manager_id")
    private Long projectManagerId;
    
    @Column(name = "parent_team", length = 30)
    private String parentTeam;

    @Builder
    public Team(String team, Long projectManagerId, String parentTeam) {
        this.team = team;
        this.projectManagerId = projectManagerId;
        this.parentTeam = parentTeam;
    }
}
