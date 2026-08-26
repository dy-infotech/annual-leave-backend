package com.dyinfotech.annualleavebackend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "team_manager")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamManager {

    @EmbeddedId
    private TeamManagerId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("teamId")
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("projectManagerId")
    @JoinColumn(name = "project_manager_id", nullable = false)
    private Employee projectManager;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_team_id", nullable = false)
    private Team parentTeam;

    @Builder
    public TeamManager(Team team, Employee projectManager, Team parentTeam) {
        this.team = team;
        this.projectManager = projectManager;
        this.parentTeam = parentTeam;
    }

    public Long getTeamId() {
        return team != null ? team.getTeamId() : null;
    }

    public Long getProjectManagerId() {
        return projectManager != null ? projectManager.getEmployeeId() : null;
    }

    public Long getParentTeamId() {
        return parentTeam != null ? parentTeam.getTeamId() : null;
    }
    
    public void changeParentTeam(Team parentTeam) {
        this.parentTeam = parentTeam;
    }
    
    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class TeamManagerId implements java.io.Serializable {

        /**
		 * 
		 */
		private static final long serialVersionUID = -1307677899283534046L;

		@Column(name = "team_id")
        private Long teamId;

        @Column(name = "project_manager_id")
        private Long projectManagerId;

        public TeamManagerId(Long teamId, Long projectManagerId) {
            this.teamId = teamId;
            this.projectManagerId = projectManagerId;
        }
    }
}
