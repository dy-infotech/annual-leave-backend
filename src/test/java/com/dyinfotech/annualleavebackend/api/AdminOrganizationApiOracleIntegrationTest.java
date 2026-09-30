package com.dyinfotech.annualleavebackend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.support.OracleIntegrationTestSupport;

class AdminOrganizationApiOracleIntegrationTest extends OracleIntegrationTestSupport {

    private Employee ceo;
    private Employee ordinary;

    @BeforeEach
    void setUpFixture() {
        ceo = seededCeo();

        Department department = department(unique("권한테스트부서"));
        Team team = team(unique("권한테스트팀"), department);
        teamManager(team, ceo, ceo.getTeam());
        ordinary = employee("일반사원", "사원", department, team, ceo, true);
    }

    @Test
    void personnelAuthority_canCreateDepartmentAndTeamWithIdempotentReplay() throws Exception {
        String departmentName = unique("신규부서");

        String departmentBody = toJson(Map.of("departmentName", departmentName));
        String departmentJson = mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminBearer(ceo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(departmentBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long departmentId = objectMapper.readTree(departmentJson).path("departmentId").asLong();
        String teamName = unique("신규팀");
        String idempotencyKey = "oracle-api-team-create-0001";
        String teamBody = toJson(Map.of(
                "teamName", teamName,
                "projectManagerId", ceo.getEmployeeId(),
                "departmentId", departmentId,
                "parentTeamId", ceo.getTeamId()));

        String firstJson = mockMvc.perform(post("/api/admin/teams")
                        .header("Authorization", adminBearer(ceo))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teamBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String replayJson = mockMvc.perform(post("/api/admin/teams")
                        .header("Authorization", adminBearer(ceo))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teamBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long firstId = objectMapper.readTree(firstJson).path("teamId").asLong();
        long replayId = objectMapper.readTree(replayJson).path("teamId").asLong();

        assertThat(replayId).isEqualTo(firstId);
    }

    @Test
    void ordinaryEmployee_cannotAccessPersonnelEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/departments")
                        .header("Authorization", bearer(ordinary)))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeRegistration_doesNotImplicitlyCreateUnknownTeam() throws Exception {
        Department existing = department(unique("등록부서"));
        String unknownTeam = unique("없는팀");

        mockMvc.perform(post("/api/admin/auth/register")
                        .header("Authorization", adminBearer(ceo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "employeeNumber", unique("NEW"),
                                "name", "신규사원",
                                "department", existing.getDepartmentName(),
                                "team", unknownTeam,
                                "position", "사원",
                                "email", "new-employee@example.com",
                                "role", "EMPLOYEE",
                                "hireDate", java.time.LocalDate.now().toString()))))
                .andExpect(status().isBadRequest());

        Long count = em.createQuery(
                        "select count(t) from Team t where t.teamName = :name",
                        Long.class)
                .setParameter("name", unknownTeam)
                .getSingleResult();
        assertThat(count).isZero();
    }
}
