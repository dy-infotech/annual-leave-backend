package com.dyinfotech.annualleavebackend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;

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
 * 결재 API(/api/admin/leave-requests) 통합 테스트.
 *
 * <p>조직 구조는 아래와 같이 두 갈래로 만든다.
 *
 * <pre>
 * 결재본부(최상위, PM=대표)
 *   +- 결재하위팀(PM=팀장)
 *        +- 팀원
 * 결재외부팀(최상위, PM=외부팀장)
 *   +- 외부팀원
 * </pre>
 */
@DisplayName("결재 API")
class LeaveApprovalApiTest extends IntegrationTestSupport {

	private static final String BASE_URL = "/api/admin/leave-requests";

	/** 팀 캐시는 애플리케이션 컨텍스트 수명 내내 살아 있으므로 테스트마다 비운다. */
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

	private Employee 대표;		// 최상위팀 PM (자기 자신이 결재자)
	private Employee 팀장;		// 하위팀 PM (결재자는 대표)
	private Employee 팀원;		// 하위팀 사원 (결재자는 팀장)
	private Employee 외부팀장;	// 결재 계보가 다른 최상위팀 PM
	private Employee 외부팀원;

	@BeforeEach
	void setUpOrganization() {
		Department department = 부서("결재부서");
		Team rootTeam = 팀("결재본부", department);
		// 최상위 사원 픽스처는 영속성 컨텍스트를 비우므로 먼저 만들고 앞선 엔티티를 다시 읽는다.
		대표 = 사원("대표", "사장", department, rootTeam, null, Role.ADMIN, 15f);
		부서 = em.find(Department.class, department.getDepartmentId());
		최상위팀 = em.find(Team.class, rootTeam.getTeamId());

		하위팀 = 팀("결재하위팀", 부서);
		외부팀 = 팀("결재외부팀", 부서);

		팀장 = 사원("팀장", "부장", 부서, 하위팀, 대표, Role.ADMIN, 15f);
		팀원 = 사원("팀원", "사원", 부서, 하위팀, 팀장, Role.EMPLOYEE, 15f);
		외부팀장 = 사원("외부팀장", "부장", 부서, 외부팀, 대표, Role.ADMIN, 15f);
		외부팀원 = 사원("외부팀원", "사원", 부서, 외부팀, 외부팀장, Role.EMPLOYEE, 15f);

		팀관리자(최상위팀, 대표, 최상위팀);		// 팀명 == 상위팀명 이면 최상위 팀
		팀관리자(하위팀, 팀장, 최상위팀);
		팀관리자(외부팀, 외부팀장, 외부팀);

		// 방금 만든 사원 엔티티는 teams 컬렉션이 빈 채로 초기화되어 있어 결재선 판정이 어긋난다.
		// 실제 요청처럼 DB에서 다시 읽도록 영속성 컨텍스트를 비운다.
		em.flush();
		em.clear();
		부서 = em.find(Department.class, 부서.getDepartmentId());
		최상위팀 = em.find(Team.class, 최상위팀.getTeamId());
		하위팀 = em.find(Team.class, 하위팀.getTeamId());
		외부팀 = em.find(Team.class, 외부팀.getTeamId());
		대표 = 사원다시읽기(대표);
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

	private LeaveRequest 신청(Employee employee, LocalDate startDate, LocalDate endDate, float useDays) {
		LeaveRequest leaveRequest = LeaveRequest.builder()
				.employee(employee)
				.leaveType(LeaveType.FULL.getName())
				.startDate(startDate)
				.endDate(endDate)
				.useDays(useDays)
				.prevTotalLeaveDays(0f)
				.leaveReason("테스트 신청")
				.build();
		em.persist(leaveRequest);
		em.flush();
		return leaveRequest;
	}

	private LeaveRequest 신청(Employee employee) {
		return 신청(employee, 올해(6, 1), 올해(6, 1), 1f);
	}

	/**
	 * 대기 이외의 상태인 신청을 만든다. 엔티티에 상태 변경 메서드가 없어 네이티브 갱신 후 다시 읽는다.
	 */
	private LeaveRequest 신청(Employee employee, LeaveRequestStatus status, String rejectReason) {
		LeaveRequest leaveRequest = 신청(employee);
		em.createNativeQuery("UPDATE leave_request SET status = ?, reject_reason = ? WHERE leave_request_id = ?")
				.setParameter(1, status.name())
				.setParameter(2, rejectReason)
				.setParameter(3, leaveRequest.getRequestId())
				.executeUpdate();
		em.refresh(leaveRequest);
		return leaveRequest;
	}

	private LeaveRequest 신청(Employee employee, LeaveRequestStatus status) {
		return 신청(employee, status, null);
	}

	private LeaveRequest 다시읽기(Long requestId) {
		em.flush();
		em.clear();
		return em.find(LeaveRequest.class, requestId);
	}

	// ------------------------------------------------------------------
	// 결재 대기 목록
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("결재 대기 목록 GET /api/admin/leave-requests/pending")
	class Pending {

		@Test
		void 최상위_팀_PM은_본인_신청과_하위_팀_PM의_신청을_본다() throws Exception {
			신청(대표);
			신청(팀장);
			신청(팀원);
			신청(외부팀원);

			// 최상위 팀 PM은 스스로 승인할 수 있으므로 본인 신청도 목록에 남는다.
			// 하위 팀은 "팀 전체"가 아니라 하위 팀 PM의 신청만 포함된다(LeaveApprovalService의 childTeamProjectManagerIds).
			mockMvc.perform(get(BASE_URL + "/pending")
					.header("Authorization", adminBearer(대표)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(2))
					.andExpect(jsonPath("$[*].employeeName", containsInAnyOrder("대표", "팀장")));
		}

		@Test
		void 하위_팀_PM은_본인_신청을_제외한_내_팀_신청만_본다() throws Exception {
			신청(대표);
			신청(팀장);
			신청(팀원);
			신청(외부팀원);

			mockMvc.perform(get(BASE_URL + "/pending")
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].employeeName").value("팀원"))
					.andExpect(jsonPath("$[0].team").value("결재하위팀"));
		}

		@Test
		void 대기_이외의_상태인_신청은_제외한다() throws Exception {
			신청(팀원);
			신청(팀원, LeaveRequestStatus.APPROVED);
			신청(팀원, LeaveRequestStatus.REJECTED);
			신청(팀원, LeaveRequestStatus.CANCELLED);

			mockMvc.perform(get(BASE_URL + "/pending")
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1));
		}

		@Test
		void 올해_범위를_벗어난_신청은_제외한다() throws Exception {
			신청(팀원, 올해(6, 1), 올해(6, 2), 2f);
			신청(팀원, 올해(1, 1).minusYears(1), 올해(12, 31).minusYears(1), 1f);
			신청(팀원, 올해(1, 1).plusYears(1), 올해(1, 2).plusYears(1), 1f);

			mockMvc.perform(get(BASE_URL + "/pending")
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].startDate").value(올해(6, 1).toString()));
		}

		@Test
		void 관리하는_팀이_없으면_빈_목록을_반환한다() throws Exception {
			신청(팀원);

			// 결재선상 관리 팀이 없는 사원은 관리자 토큰을 들고 있어도 볼 수 있는 신청이 없다.
			mockMvc.perform(get(BASE_URL + "/pending")
					.header("Authorization", adminBearer(팀원)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$").value(empty()));
		}
	}

	// ------------------------------------------------------------------
	// 승인 목록
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("승인 목록 GET /api/admin/leave-requests/approved")
	class Approved {

		@Test
		void 내_팀과_하위_팀의_승인_건을_조회한다() throws Exception {
			신청(대표, LeaveRequestStatus.APPROVED);
			신청(팀원, LeaveRequestStatus.APPROVED);
			신청(외부팀원, LeaveRequestStatus.APPROVED);
			신청(팀원);	// 대기 건은 제외

			mockMvc.perform(get(BASE_URL + "/approved")
					.header("Authorization", adminBearer(대표)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(2))
					.andExpect(jsonPath("$[*].employeeName", containsInAnyOrder("대표", "팀원")))
					.andExpect(jsonPath("$[*].employeeName", not(hasItem("외부팀원"))))
					.andExpect(jsonPath("$[*].status", containsInAnyOrder("APPROVED", "APPROVED")));
		}

		@Test
		void team_파라미터로_특정_팀만_조회한다() throws Exception {
			신청(대표, LeaveRequestStatus.APPROVED);
			신청(팀원, LeaveRequestStatus.APPROVED);

			mockMvc.perform(get(BASE_URL + "/approved")
					.param("team", "결재하위팀")
					.header("Authorization", adminBearer(대표)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].employeeName").value("팀원"));
		}

		@Test
		void 접근할_수_없는_팀을_지정하면_조용히_무시된다() throws Exception {
			신청(대표, LeaveRequestStatus.APPROVED);
			신청(팀원, LeaveRequestStatus.APPROVED);
			신청(외부팀원, LeaveRequestStatus.APPROVED);

			// 접근 불가 팀을 지정하면 400/403이 아니라 파라미터를 무시하고 접근 가능한 전체 팀을 조회한다.
			mockMvc.perform(get(BASE_URL + "/approved")
					.param("team", "결재외부팀")
					.header("Authorization", adminBearer(대표)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(2))
					.andExpect(jsonPath("$[*].employeeName", containsInAnyOrder("대표", "팀원")));
		}

		@Test
		void employeeParam으로_사번을_대소문자_구분_없이_부분_일치_조회한다() throws Exception {
			신청(대표, LeaveRequestStatus.APPROVED);
			신청(팀원, LeaveRequestStatus.APPROVED);

			String 사번일부 = 팀원.getEmployeeNumber().substring(1).toLowerCase();	// 접두어 T를 뗀 소문자

			mockMvc.perform(get(BASE_URL + "/approved")
					.param("employeeParam", 사번일부)
					.header("Authorization", adminBearer(대표)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].employeeNumber").value(팀원.getEmployeeNumber()));
		}

		@Test
		void employeeParam으로_이름을_부분_일치_조회한다() throws Exception {
			신청(대표, LeaveRequestStatus.APPROVED);
			신청(팀원, LeaveRequestStatus.APPROVED);

			mockMvc.perform(get(BASE_URL + "/approved")
					.param("employeeParam", "팀")
					.header("Authorization", adminBearer(대표)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].employeeName").value("팀원"));
		}

		@Test
		void 올해_범위를_벗어난_승인_건은_제외한다() throws Exception {
			LeaveRequest 작년건 = 신청(팀원, 올해(1, 1).minusYears(1), 올해(12, 31).minusYears(1), 1f);
			em.createNativeQuery("UPDATE leave_request SET status = 'APPROVED' WHERE leave_request_id = ?")
					.setParameter(1, 작년건.getRequestId())
					.executeUpdate();
			신청(팀원, LeaveRequestStatus.APPROVED);

			mockMvc.perform(get(BASE_URL + "/approved")
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].startDate").value(올해(6, 1).toString()));
		}

		@Test
		void 관리하는_팀이_없으면_빈_목록을_반환한다() throws Exception {
			신청(팀원, LeaveRequestStatus.APPROVED);

			mockMvc.perform(get(BASE_URL + "/approved")
					.header("Authorization", adminBearer(팀원)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$").value(empty()));
		}
	}

	// ------------------------------------------------------------------
	// 반려 목록
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("반려 목록 GET /api/admin/leave-requests/rejected")
	class Rejected {

		@Test
		void 내_팀과_하위_팀의_반려_건을_사유와_함께_조회한다() throws Exception {
			신청(팀원, LeaveRequestStatus.REJECTED, "일정 조정 필요");
			신청(외부팀원, LeaveRequestStatus.REJECTED, "다른 팀 건");
			신청(팀원, LeaveRequestStatus.APPROVED);

			mockMvc.perform(get(BASE_URL + "/rejected")
					.header("Authorization", adminBearer(대표)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].employeeName").value("팀원"))
					.andExpect(jsonPath("$[0].status").value("REJECTED"))
					.andExpect(jsonPath("$[0].rejectReason").value("일정 조정 필요"));
		}

		@Test
		void team_파라미터로_특정_팀만_조회한다() throws Exception {
			신청(대표, LeaveRequestStatus.REJECTED, "대표 건");
			신청(팀원, LeaveRequestStatus.REJECTED, "팀원 건");

			mockMvc.perform(get(BASE_URL + "/rejected")
					.param("team", "결재본부")
					.header("Authorization", adminBearer(대표)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].employeeName").value("대표"));
		}

		@Test
		void employeeParam으로_부분_일치_조회한다() throws Exception {
			신청(대표, LeaveRequestStatus.REJECTED, "대표 건");
			신청(팀원, LeaveRequestStatus.REJECTED, "팀원 건");

			mockMvc.perform(get(BASE_URL + "/rejected")
					.param("employeeParam", "팀원")
					.header("Authorization", adminBearer(대표)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].employeeName").value("팀원"));
		}

		@Test
		void 관리하는_팀이_없으면_빈_목록을_반환한다() throws Exception {
			신청(팀원, LeaveRequestStatus.REJECTED, "사유");

			mockMvc.perform(get(BASE_URL + "/rejected")
					.header("Authorization", adminBearer(팀원)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$").value(empty()));
		}
	}

	// ------------------------------------------------------------------
	// 승인
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("휴가 승인 POST /api/admin/leave-requests/{requestId}/approve")
	class Approve {

		@Test
		void 대기_상태_신청을_승인한다() throws Exception {
			Long requestId = 신청(팀원).getRequestId();
			Long 팀장id = 팀장.getEmployeeId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/approve")
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.requestId").value(requestId))
					.andExpect(jsonPath("$.status").value("APPROVED"))
					.andExpect(jsonPath("$.managerName").value("팀장"))
					.andExpect(jsonPath("$.managedAt").isNotEmpty());

			LeaveRequest 처리결과 = 다시읽기(requestId);
			assertThat(처리결과.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
			assertThat(처리결과.getManager().getEmployeeId()).isEqualTo(팀장id);
			assertThat(처리결과.getManagedAt()).isNotNull();
			assertThat(처리결과.getRejectReason()).isNull();
		}

		@Test
		void 최상위_팀_PM은_본인_신청을_스스로_승인한다() throws Exception {
			Long requestId = 신청(대표).getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/approve")
					.header("Authorization", adminBearer(대표)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("APPROVED"))
					.andExpect(jsonPath("$.managerName").value("대표"));

			assertThat(다시읽기(requestId).getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
		}

		@Test
		void 이미_승인된_건을_다시_승인하면_409를_반환한다() throws Exception {
			Long requestId = 신청(팀원, LeaveRequestStatus.APPROVED).getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/approve")
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.message").value("해당 요청은 이미 처리되었습니다."));
		}

		@Test
		void 이미_반려된_건을_승인하면_409를_반환한다() throws Exception {
			Long requestId = 신청(팀원, LeaveRequestStatus.REJECTED, "사유").getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/approve")
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.message").value("해당 요청은 이미 처리되었습니다."));
		}

		@Test
		void 취소된_건을_승인하면_409를_반환한다() throws Exception {
			Long requestId = 신청(팀원, LeaveRequestStatus.CANCELLED).getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/approve")
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.message").value("해당 요청은 이미 처리되었습니다."));
		}

		@Test
		void 존재하지_않는_신청이면_404를_반환한다() throws Exception {
			mockMvc.perform(post(BASE_URL + "/99999999/approve")
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.message").value("존재하지 않는 휴가 신청 정보입니다."));
		}

		@Test
		void 결재선에_없는_관리자가_승인하면_404를_반환한다() throws Exception {
			Long requestId = 신청(팀원).getRequestId();

			// 팀원의 결재자는 하위팀 PM인 팀장뿐이다. 상위 팀 PM인 대표라도 직접 승인할 수 없다.
			// 실제 동작은 권한 오류(403)가 아니라 404 + "승인할 수 없는 관리자입니다." 이다.
			mockMvc.perform(post(BASE_URL + "/" + requestId + "/approve")
					.header("Authorization", adminBearer(대표)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.message").value("승인할 수 없는 관리자입니다."));

			assertThat(다시읽기(requestId).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
		}

		@Test
		void 결재_계보가_다른_팀의_PM이_승인하면_404를_반환한다() throws Exception {
			Long requestId = 신청(팀원).getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/approve")
					.header("Authorization", adminBearer(외부팀장)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.message").value("승인할 수 없는 관리자입니다."));

			assertThat(다시읽기(requestId).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
		}
	}

	// ------------------------------------------------------------------
	// 반려
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("휴가 반려 POST /api/admin/leave-requests/{requestId}/reject")
	class Reject {

		@Test
		void 사유와_함께_반려한다() throws Exception {
			Long requestId = 신청(팀원).getRequestId();
			Long 팀장id = 팀장.getEmployeeId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/reject")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("rejectReason", "해당 기간은 프로젝트 마감입니다."))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.requestId").value(requestId))
					.andExpect(jsonPath("$.status").value("REJECTED"))
					.andExpect(jsonPath("$.managerName").value("팀장"))
					.andExpect(jsonPath("$.rejectReason").value("해당 기간은 프로젝트 마감입니다."))
					.andExpect(jsonPath("$.managedAt").isNotEmpty());

			LeaveRequest 처리결과 = 다시읽기(requestId);
			assertThat(처리결과.getStatus()).isEqualTo(LeaveRequestStatus.REJECTED);
			assertThat(처리결과.getRejectReason()).isEqualTo("해당 기간은 프로젝트 마감입니다.");
			assertThat(처리결과.getManager().getEmployeeId()).isEqualTo(팀장id);
			assertThat(처리결과.getManagedAt()).isNotNull();
		}

		@Test
		void 사유_없이_반려하면_사유가_비어_있는_채로_저장된다() throws Exception {
			Long requestId = 신청(팀원).getRequestId();

			// rejectReason은 선택 입력이라 본문이 비어 있어도 400이 아니라 200이다.
			mockMvc.perform(post(BASE_URL + "/" + requestId + "/reject")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("REJECTED"))
					.andExpect(jsonPath("$.rejectReason").doesNotExist());

			LeaveRequest 처리결과 = 다시읽기(requestId);
			assertThat(처리결과.getStatus()).isEqualTo(LeaveRequestStatus.REJECTED);
			assertThat(처리결과.getRejectReason()).isNull();
		}

		@Test
		void 이미_반려된_건을_다시_반려하면_409를_반환한다() throws Exception {
			Long requestId = 신청(팀원, LeaveRequestStatus.REJECTED, "먼저 반려한 사유").getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/reject")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("rejectReason", "두 번째 반려"))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.message").value("해당 요청은 이미 처리되었습니다."));

			assertThat(다시읽기(requestId).getRejectReason()).isEqualTo("먼저 반려한 사유");
		}

		@Test
		void 이미_승인된_건을_반려하면_409를_반환한다() throws Exception {
			Long requestId = 신청(팀원, LeaveRequestStatus.APPROVED).getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/reject")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("rejectReason", "반려 시도"))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.message").value("해당 요청은 이미 처리되었습니다."));
		}

		@Test
		void 취소된_건을_반려하면_409를_반환한다() throws Exception {
			Long requestId = 신청(팀원, LeaveRequestStatus.CANCELLED).getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/reject")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("rejectReason", "반려 시도"))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.message").value("해당 요청은 이미 처리되었습니다."));
		}

		@Test
		void 존재하지_않는_신청이면_404를_반환한다() throws Exception {
			mockMvc.perform(post(BASE_URL + "/99999999/reject")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("rejectReason", "사유"))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.message").value("존재하지 않는 휴가 신청 정보입니다."));
		}

		@Test
		void 결재선에_없는_관리자가_반려하면_404를_반환한다() throws Exception {
			Long requestId = 신청(팀원).getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/reject")
					.header("Authorization", adminBearer(대표))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("rejectReason", "사유"))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.message").value("승인할 수 없는 관리자입니다."));

			assertThat(다시읽기(requestId).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
		}
	}

	// ------------------------------------------------------------------
	// 접근 권한
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("접근 권한 (hasRole ADMIN)")
	class Authorization {

		// 인증/인가 실패 시 현재 구현은 401이 아니라 403으로 응답한다.
		// (AuthenticationEntryPoint 미설정 시의 Spring Security 기본 동작)
		@Test
		void 사원_권한_토큰으로_결재_대기_목록을_호출하면_403을_반환한다() throws Exception {
			mockMvc.perform(get(BASE_URL + "/pending")
					.header("Authorization", bearer(팀장, Role.EMPLOYEE)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 토큰_없이_결재_대기_목록을_호출하면_403을_반환한다() throws Exception {
			mockMvc.perform(get(BASE_URL + "/pending"))
					.andExpect(status().isForbidden());
		}

		@Test
		void 사원_권한_토큰으로_승인을_호출하면_403을_반환한다() throws Exception {
			Long requestId = 신청(팀원).getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/approve")
					.header("Authorization", bearer(팀장, Role.EMPLOYEE)))
					.andExpect(status().isForbidden());

			assertThat(다시읽기(requestId).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
		}

		@Test
		void 토큰_없이_승인을_호출하면_403을_반환한다() throws Exception {
			Long requestId = 신청(팀원).getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/approve"))
					.andExpect(status().isForbidden());

			assertThat(다시읽기(requestId).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
		}

		@Test
		void 사원_권한_토큰으로_반려를_호출하면_403을_반환한다() throws Exception {
			Long requestId = 신청(팀원).getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/reject")
					.header("Authorization", bearer(팀장, Role.EMPLOYEE))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("rejectReason", "사유"))))
					.andExpect(status().isForbidden());

			assertThat(다시읽기(requestId).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
		}

		@Test
		void 토큰_없이_반려를_호출하면_403을_반환한다() throws Exception {
			Long requestId = 신청(팀원).getRequestId();

			mockMvc.perform(post(BASE_URL + "/" + requestId + "/reject")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("rejectReason", "사유"))))
					.andExpect(status().isForbidden());

			assertThat(다시읽기(requestId).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
		}
	}
}
