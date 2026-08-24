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

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Builder
    public Team(String teamName, Boolean enabled) {
        this.teamName = teamName;
        this.enabled = enabled;
    }
    
    public void disable() {
        this.enabled = false;
    }
}