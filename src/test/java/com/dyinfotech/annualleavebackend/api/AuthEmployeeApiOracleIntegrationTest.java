package com.dyinfotech.annualleavebackend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

class AuthEmployeeApiOracleIntegrationTest extends OracleIntegrationTestSupport {

    private Employee registered;
    private Employee unregistered;

    @BeforeEach
    void setUpFixture() {
        Employee ceo = seededCeo();
        Department department = department(unique("API인증부서"));
        Team team = team(unique("API인증팀"), department);
        teamManager(team, ceo, ceo.getTeam());

        registered = employee("등록사원", "과장", department, team, ceo, true);
        unregistered = employee("미등록사원", "사원", department, team, ceo, false);
    }

    @Test
    void signUpThenSignIn_worksAgainstOracleSchema() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "employeeNumber", unregistered.getEmployeeNumber(),
                                "password", DEFAULT_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("미등록사원"));

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "employeeNumber", unregistered.getEmployeeNumber(),
                                "password", DEFAULT_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.employeeId").value(unregistered.getEmployeeId()));
    }

    @Test
    void myInfo_usesCurrentOrganizationAndApprover() throws Exception {
        mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", bearer(registered)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeNumber").value(registered.getEmployeeNumber()))
                .andExpect(jsonPath("$.name").value("등록사원"))
                .andExpect(jsonPath("$.team").value(registered.getTeamName()))
                .andExpect(jsonPath("$.approverName").value("우동영"));
    }

    @Test
    void changeEmail_validatesSchemaLengthBeforeOracleError() throws Exception {
        mockMvc.perform(patch("/api/employees/me/email")
                        .header("Authorization", bearer(registered))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", "x".repeat(90) + "@example.com"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/employees/me/email")
                        .header("Authorization", bearer(registered))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", "changed@example.com"))))
                .andExpect(status().isOk());

        em.flush();
        em.clear();
        Employee updated = em.find(Employee.class, registered.getEmployeeId());
        assertThat(updated.getEmail()).isEqualTo("changed@example.com");
    }
}
