package com.dyinfotech.annualleavebackend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.support.OracleIntegrationTestSupport;

class LeaveDashboardApiOracleIntegrationTest extends OracleIntegrationTestSupport {

    private Employee employee;

    @BeforeEach
    void setUpFixture() {
        Employee ceo = seededCeo();
        Department department = department(unique("휴가API부서"));
        Team team = team(unique("휴가API팀"), department);
        teamManager(team, ceo, ceo.getTeam());
        employee = employee("휴가신청자", "과장", department, team, ceo, true);
    }

    @Test
    void leavePeriodAndDashboard_followCurrentFiscalYear() throws Exception {
        int year = LocalDate.now().getYear();

        mockMvc.perform(get("/api/leave-requests/my/period")
                        .header("Authorization", bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(year + "-01-01"))
                .andExpect(jsonPath("$.endDate").value(year + "-12-31"));

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myLeavePeriod.startDate").value(year + "-01-01"))
                .andExpect(jsonPath("$.myLeavePeriod.endDate").value(year + "-12-31"))
                .andExpect(jsonPath("$.myLeaveInfoResponse").exists())
                .andExpect(jsonPath("$.myRequestSummary").exists());
    }

    @Test
    void createAndReadOwnLeaveDetail_preservesReasonAndSnapshot() throws Exception {
        LocalDate leaveDate = nextBusinessDayInCurrentYear();
        String response = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "leaveType", "FULL",
                                "startDate", leaveDate.toString(),
                                "endDate", leaveDate.toString(),
                                "useDays", 1.0,
                                "leaveReason", "Oracle API 통합테스트"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long requestId = objectMapper.readTree(response).path("requestId").asLong();

        // 외부 알림은 DB commit 이후에만 나가야 한다. 통합테스트 transaction은 아직 미커밋 상태다.
        verify(notificationService, never()).sendNotificationToTeams(any(), any(), any());

        mockMvc.perform(get("/api/leave-requests/{requestId}", requestId)
                        .header("Authorization", bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveReason").value("Oracle API 통합테스트"))
                .andExpect(jsonPath("$.prevTotalLeaveDays").isNumber())
                .andExpect(jsonPath("$.currTotalLeaveDays").isNumber());
    }

    private LocalDate nextBusinessDayInCurrentYear() {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        if (date.getYear() != LocalDate.now().getYear()) {
            throw new IllegalStateException("연말에는 테스트용 미래 근무일을 현재 회계연도에서 선택할 수 없습니다.");
        }
        return date;
    }
}
