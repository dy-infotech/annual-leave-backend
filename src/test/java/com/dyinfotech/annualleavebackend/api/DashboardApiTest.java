package com.dyinfotech.annualleavebackend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.type.LeaveType;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.support.IntegrationTestSupport;
import com.github.benmanes.caffeine.cache.LoadingCache;

/**
 * 대시보드 API(/api/dashboard) 통합 테스트.
 *
 * <pre>
 * 대시본부(최상위, PM=본부장)
 *   +- 대시하위팀(PM=대시팀장)
 *        +- 대시팀원
 * 대시외부팀(최상위, PM=대시외부팀장)
 *   +- 대시외부팀원
 * </pre>
 */
@DisplayName("대시보드 API")
class DashboardApiTest extends IntegrationTestSupport {

	private static final String BASE_URL = "/api/dashboard";

	/** 입사 3년차(픽스처 기준) 사원의 계산 연차. 기초 데이터(기본 15일 + 가산 1일)에서 나온다. */
	private static final float 계산된_연차일수 = 16f;

	@Autowired
	@Qualifier("teamLoadingCache")
	private LoadingCache<String, List<Team>> teamCache;

	@Autowired
	@Qualifier("teamManagerLoadingCache")
	private LoadingCache<String, List<TeamManager>> teamManagerCache;

	@Autowired
	private CacheManager cacheManager;

	private Department 부서;
	private Team 최상위팀;
	private Team 하위팀;
	private Team 외부팀;

	private Employee 본부장;		// 최상위팀 PM
	private Employee 팀장;		// 하위팀 PM
	private Employee 팀원;		// PM이 아닌 일반 사원
	private Employee 외부팀장;
	private Employee 외부팀원;

	@BeforeEach
	void setUpOrganization() {
		Department department = 부서("대시보드부서");
		Team rootTeam = 팀("대시본부", department);
		// 최상위 사원 픽스처는 영속성 컨텍스트를 비우므로 먼저 만들고 앞선 엔티티를 다시 읽는다.
		본부장 = 사원("본부장", "사장", department, rootTeam, null, Role.ADMIN, 15f);
		부서 = em.find(Department.class, department.getDepartmentId());
		최상위팀 = em.find(Team.class, rootTeam.getTeamId());

		하위팀 = 팀("대시하위팀", 부서);
		외부팀 = 팀("대시외부팀", 부서);

		팀장 = 사원("대시팀장", "부장", 부서, 하위팀, 본부장, Role.ADMIN, 15f);
		팀원 = 사원("대시팀원", "사원", 부서, 하위팀, 팀장, Role.EMPLOYEE, 15f);
		외부팀장 = 사원("대시외부팀장", "부장", 부서, 외부팀, 본부장, Role.ADMIN, 15f);
		외부팀원 = 사원("대시외부팀원", "사원", 부서, 외부팀, 외부팀장, Role.EMPLOYEE, 15f);

		팀관리자(최상위팀, 본부장, 최상위팀);		// 팀명 == 상위팀명 이면 최상위 팀
		팀관리자(하위팀, 팀장, 최상위팀);
		팀관리자(외부팀, 외부팀장, 외부팀);

		// 방금 만든 사원 엔티티는 teams 컬렉션이 빈 채로 초기화되어 있어 관리 팀 판정이 어긋난다.
		// 실제 요청처럼 DB에서 다시 읽도록 영속성 컨텍스트를 비운다.
		em.flush();
		em.clear();
		부서 = em.find(Department.class, 부서.getDepartmentId());
		최상위팀 = em.find(Team.class, 최상위팀.getTeamId());
		하위팀 = em.find(Team.class, 하위팀.getTeamId());
		외부팀 = em.find(Team.class, 외부팀.getTeamId());
		본부장 = 사원다시읽기(본부장);
		팀장 = 사원다시읽기(팀장);
		팀원 = 사원다시읽기(팀원);
		외부팀장 = 사원다시읽기(외부팀장);
		외부팀원 = 사원다시읽기(외부팀원);

		캐시비우기();
	}

	private Employee 사원다시읽기(Employee employee) {
		return em.find(Employee.class, employee.getEmployeeId());
	}

	@AfterEach
	void clearCaches() {
		// 롤백된 픽스처가 캐시에 남아 다른 테스트를 오염시키지 않도록 비운다.
		캐시비우기();
	}

	private void 캐시비우기() {
		teamCache.invalidateAll();
		teamManagerCache.invalidateAll();
		cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
	}

	// ------------------------------------------------------------------
	// 픽스처
	// ------------------------------------------------------------------

	private LocalDate 올해(int month, int day) {
		return LocalDate.of(Year.now().getValue(), month, day);
	}

	private LeaveRequest 신청(Employee employee, LocalDate startDate, float useDays) {
		LeaveRequest leaveRequest = LeaveRequest.builder()
				.employee(employee)
				.leaveType(LeaveType.FULL.getName())
				.startDate(startDate)
				.endDate(startDate)
				.useDays(useDays)
				.prevTotalLeaveDays(0f)
				.leaveReason("테스트 신청")
				.build();
		em.persist(leaveRequest);
		em.flush();
		return leaveRequest;
	}

	private LeaveRequest 신청(Employee employee) {
		return 신청(employee, 올해(6, 1), 1f);
	}

	/** 대기 이외의 상태인 신청을 만든다. 엔티티에 상태 변경 메서드가 없어 네이티브 갱신 후 다시 읽는다. */
	private LeaveRequest 신청(Employee employee, LeaveRequestStatus status) {
		LeaveRequest leaveRequest = 신청(employee);
		if (status != LeaveRequestStatus.PENDING) {
			em.createNativeQuery("UPDATE leave_request SET status = ? WHERE leave_request_id = ?")
					.setParameter(1, status.name())
					.setParameter(2, leaveRequest.getRequestId())
					.executeUpdate();
			em.refresh(leaveRequest);
		}
		return leaveRequest;
	}

	/**
	 * 집계 검증용 공통 신청 픽스처.
	 *
	 * <pre>
	 * 팀원   : 대기 2, 승인 1, 반려 1, 취소 1
	 * 팀장   : 대기 1, 승인 1
	 * 본부장 : 대기 1
	 * 외부팀원: 대기 1, 승인 1  (다른 결재 계보라 집계에서 빠져야 한다)
	 * </pre>
	 */
	private void 집계용_신청_생성() {
		신청(팀원);
		신청(팀원);
		신청(팀원, LeaveRequestStatus.APPROVED);
		신청(팀원, LeaveRequestStatus.REJECTED);
		신청(팀원, LeaveRequestStatus.CANCELLED);

		신청(팀장);
		신청(팀장, LeaveRequestStatus.APPROVED);

		신청(본부장);

		신청(외부팀원);
		신청(외부팀원, LeaveRequestStatus.APPROVED);
	}

	// ------------------------------------------------------------------
	// 일반 사원
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("일반 사원 GET /api/dashboard")
	class Member {

		@Test
		void 내_연차_정보와_내_신청_현황을_받고_전직원_요약은_없다() throws Exception {
			집계용_신청_생성();

			// 사용일수는 대기 + 승인 건의 합이다(반려/취소는 제외). 팀원은 대기 2 + 승인 1 = 3일.
			mockMvc.perform(get(BASE_URL)
					.header("Authorization", bearer(팀원)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.myLeaveInfoResponse.totalLeaveDays").value(계산된_연차일수))
					.andExpect(jsonPath("$.myLeaveInfoResponse.usedLeaveDays").value(3.0))
					.andExpect(jsonPath("$.myLeaveInfoResponse.remainingLeaveDays").value(계산된_연차일수 - 3.0))
					.andExpect(jsonPath("$.myRequestSummary.pendingCount").value(2))
					.andExpect(jsonPath("$.myRequestSummary.approvedCount").value(1))
					.andExpect(jsonPath("$.myRequestSummary.rejectedCount").value(1))
					.andExpect(jsonPath("$.allEmployeeRequestSummary").doesNotExist());
		}

		@Test
		void 신청이_없으면_현황은_모두_0이고_연차는_전부_남는다() throws Exception {
			mockMvc.perform(get(BASE_URL)
					.header("Authorization", bearer(팀원)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.myLeaveInfoResponse.usedLeaveDays").value(0.0))
					.andExpect(jsonPath("$.myLeaveInfoResponse.remainingLeaveDays").value(계산된_연차일수))
					.andExpect(jsonPath("$.myRequestSummary.pendingCount").value(0))
					.andExpect(jsonPath("$.myRequestSummary.approvedCount").value(0))
					.andExpect(jsonPath("$.myRequestSummary.rejectedCount").value(0));
		}

		@Test
		void 올해_범위를_벗어난_신청은_집계에서_빠진다() throws Exception {
			신청(팀원);
			신청(팀원, 올해(6, 1).minusYears(1), 1f);
			신청(팀원, 올해(6, 1).plusYears(1), 1f);

			mockMvc.perform(get(BASE_URL)
					.header("Authorization", bearer(팀원)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.myRequestSummary.pendingCount").value(1))
					.andExpect(jsonPath("$.myLeaveInfoResponse.usedLeaveDays").value(1.0));
		}

		@Test
		void 관리자_권한_토큰이어도_PM이_아니면_전직원_요약은_없다() throws Exception {
			집계용_신청_생성();

			// 전직원 요약 노출 여부는 토큰의 role이 아니라 team_manager 등록 여부로 결정된다.
			mockMvc.perform(get(BASE_URL)
					.header("Authorization", adminBearer(팀원)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.myRequestSummary.pendingCount").value(2))
					.andExpect(jsonPath("$.allEmployeeRequestSummary").doesNotExist());
		}
	}

	// ------------------------------------------------------------------
	// 관리자(PM)
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("관리자 GET /api/dashboard")
	class Manager {

		@Test
		void 하위_팀_PM은_내_팀_집계를_전직원_요약으로_받는다() throws Exception {
			집계용_신청_생성();

			// 대기는 본인을 제외한 내 팀(대시하위팀) 건만 센다 -> 팀원 2건
			// 승인/반려는 본인 것을 포함한 내 팀 + 하위 팀 건을 센다 -> 승인 팀원 1 + 팀장 1, 반려 팀원 1
			mockMvc.perform(get(BASE_URL)
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.myRequestSummary.pendingCount").value(1))
					.andExpect(jsonPath("$.myRequestSummary.approvedCount").value(1))
					.andExpect(jsonPath("$.allEmployeeRequestSummary.pendingCount").value(2))
					.andExpect(jsonPath("$.allEmployeeRequestSummary.approvedCount").value(2))
					.andExpect(jsonPath("$.allEmployeeRequestSummary.rejectedCount").value(1));
		}

		@Test
		void 최상위_팀_PM은_본인과_하위_팀_PM의_대기_건을_함께_받는다() throws Exception {
			집계용_신청_생성();

			// 최상위 팀 PM은 스스로 승인할 수 있어 본인 대기 건이 빠지지 않는다.
			// 대기는 내 팀(대시본부) 전체 + 하위 팀 PM(팀장) -> 본부장 1 + 팀장 1 = 2
			// 승인/반려는 내 팀 + 모든 하위 팀 -> 승인 팀원 1 + 팀장 1 = 2, 반려 팀원 1
			mockMvc.perform(get(BASE_URL)
					.header("Authorization", adminBearer(본부장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.myRequestSummary.pendingCount").value(1))
					.andExpect(jsonPath("$.allEmployeeRequestSummary.pendingCount").value(2))
					.andExpect(jsonPath("$.allEmployeeRequestSummary.approvedCount").value(2))
					.andExpect(jsonPath("$.allEmployeeRequestSummary.rejectedCount").value(1));
		}

		@Test
		void 사원_권한_토큰이어도_PM이면_전직원_요약을_받는다() throws Exception {
			집계용_신청_생성();

			// DashboardController가 토큰의 role을 넘기지만 DashboardService는 이 값을 쓰지 않는다.
			mockMvc.perform(get(BASE_URL)
					.header("Authorization", bearer(팀장, Role.EMPLOYEE)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.allEmployeeRequestSummary.pendingCount").value(2))
					.andExpect(jsonPath("$.allEmployeeRequestSummary.approvedCount").value(2))
					.andExpect(jsonPath("$.allEmployeeRequestSummary.rejectedCount").value(1));
		}

		@Test
		void 다른_결재_계보의_신청은_전직원_요약에서_빠진다() throws Exception {
			신청(외부팀원);
			신청(외부팀원, LeaveRequestStatus.APPROVED);
			신청(외부팀원, LeaveRequestStatus.REJECTED);

			mockMvc.perform(get(BASE_URL)
					.header("Authorization", adminBearer(본부장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.allEmployeeRequestSummary.pendingCount").value(0))
					.andExpect(jsonPath("$.allEmployeeRequestSummary.approvedCount").value(0))
					.andExpect(jsonPath("$.allEmployeeRequestSummary.rejectedCount").value(0));
		}

		@Test
		void 올해_범위를_벗어난_신청은_전직원_요약에서도_빠진다() throws Exception {
			신청(팀원);
			신청(팀원, 올해(6, 1).minusYears(1), 1f);
			신청(팀원, 올해(6, 1).plusYears(1), 1f);

			mockMvc.perform(get(BASE_URL)
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.allEmployeeRequestSummary.pendingCount").value(1));
		}

		@Test
		void 취소된_신청은_어느_집계에도_잡히지_않는다() throws Exception {
			신청(팀원, LeaveRequestStatus.CANCELLED);

			mockMvc.perform(get(BASE_URL)
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.allEmployeeRequestSummary.pendingCount").value(0))
					.andExpect(jsonPath("$.allEmployeeRequestSummary.approvedCount").value(0))
					.andExpect(jsonPath("$.allEmployeeRequestSummary.rejectedCount").value(0));
		}
	}

	// ------------------------------------------------------------------
	// 예외
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("예외 GET /api/dashboard")
	class Failure {

		@Test
		void 존재하지_않는_직원_토큰이면_404를_반환한다() throws Exception {
			String 없는사원토큰 = "Bearer " + jwtProvider.generateToken(99999999L, Role.EMPLOYEE.name());

			mockMvc.perform(get(BASE_URL)
					.header("Authorization", 없는사원토큰))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.message").value("존재하지 않는 직원입니다."));
		}

		// 인증 실패 시 현재 구현은 401이 아니라 403으로 응답한다.
		// (AuthenticationEntryPoint 미설정 시의 Spring Security 기본 동작)
		@Test
		void 토큰_없이_호출하면_403을_반환한다() throws Exception {
			mockMvc.perform(get(BASE_URL))
					.andExpect(status().isForbidden());
		}

		@Test
		void 위조된_토큰이면_403을_반환한다() throws Exception {
			mockMvc.perform(get(BASE_URL)
					.header("Authorization", "Bearer this.is.not.a.valid.token"))
					.andExpect(status().isForbidden());
		}
	}
}
