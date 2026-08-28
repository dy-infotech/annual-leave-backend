package com.dyinfotech.annualleavebackend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;

import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.support.IntegrationTestSupport;
import com.github.benmanes.caffeine.cache.LoadingCache;

/**
 * 관리자 팀 API(/api/admin/teams) 통합 테스트.
 *
 * <p>모든 엔드포인트가 인사권(사장 직급)을 요구한다.
 */
@DisplayName("관리자 팀 API")
class AdminTeamApiTest extends IntegrationTestSupport {

	@Autowired
	@Qualifier("departmentLoadingCache")
	private LoadingCache<String, List<Department>> departmentCache;

	@Autowired
	@Qualifier("teamLoadingCache")
	private LoadingCache<String, List<Team>> teamCache;

	@Autowired
	@Qualifier("teamManagerLoadingCache")
	private LoadingCache<String, List<TeamManager>> teamManagerCache;

	@Autowired
	private CacheManager cacheManager;

	private Department 부서;
	private Team 팀;
	private Employee 사장;
	private Employee 이사;
	private Employee 일반사원;

	@BeforeEach
	void setUpOrganization() {
		// 캐시는 애플리케이션 컨텍스트 단위로 공유되므로 이전 테스트의 롤백된 데이터가 남지 않도록 비운다.
		clearCaches();

		부서 = 부서("팀관리테스트부서");
		팀 = 팀("팀관리테스트팀", 부서);
		사장 = 사원("사장님", "사장", 부서, 팀, null, Role.ADMIN, 15f);
		팀관리자(팀, 사장, 팀);
		이사 = 사원("이사님", "이사", 부서, 팀, 사장, Role.ADMIN, 15f);
		일반사원 = 사원("사원", "사원", 부서, 팀, 사장, Role.EMPLOYEE, 15f);
		em.flush();
	}

	private void clearCaches() {
		departmentCache.invalidateAll();
		teamCache.invalidateAll();
		teamManagerCache.invalidateAll();
		cacheManager.getCacheNames()
				.forEach(name -> cacheManager.getCache(name).clear());
	}

	private Department 비활성부서(String name) {
		Department department = Department.builder()
				.departmentName(name)
				.enabled(Boolean.FALSE)
				.build();
		em.persist(department);
		em.flush();
		return department;
	}

	private Team 비활성팀(String name, Department department) {
		Team team = Team.builder()
				.teamName(name)
				.enabled(Boolean.FALSE)
				.department(department)
				.build();
		em.persist(team);
		em.flush();
		return team;
	}

	private void 초기화() {
		em.flush();
		em.clear();
	}

	private List<TeamManager> 담당자목록(Long teamId) {
		return em.createQuery("select tm from TeamManager tm where tm.team.teamId = :teamId", TeamManager.class)
				.setParameter("teamId", teamId)
				.getResultList();
	}

	@Nested
	@DisplayName("팀 목록 조회 GET /api/admin/teams")
	class GetTeams {

		@Test
		void 사장이면_담당자와_상위_팀_정보를_함께_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/teams")
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[?(@.teamName=='팀관리테스트팀')]").exists())
					.andExpect(jsonPath("$[?(@.teamName=='팀관리테스트팀')].departmentName")
							.value("팀관리테스트부서"))
					.andExpect(jsonPath("$[?(@.teamName=='팀관리테스트팀')].parentTeamName")
							.value("팀관리테스트팀"))
					.andExpect(jsonPath("$[?(@.teamName=='팀관리테스트팀')].managers[0].name")
							.value("사장님"));
		}

		@Test
		void 비활성_팀은_목록에서_빠진다() throws Exception {
			비활성팀("비활성팀", 부서);

			mockMvc.perform(get("/api/admin/teams")
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[?(@.teamName=='비활성팀')]").doesNotExist());
		}

		@Test
		void 사장이_아닌_관리자면_403을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/teams")
					.header("Authorization", adminBearer(이사)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 일반_사원_토큰이면_403을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/teams")
					.header("Authorization", bearer(일반사원)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 토큰이_없으면_403을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/teams"))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("팀 등록 POST /api/admin/teams")
	class CreateTeam {

		@Test
		void 상위_팀을_지정하지_않으면_요청자의_팀이_상위_팀이_된다() throws Exception {
			Long 상위팀번호 = 팀.getTeamId();

			mockMvc.perform(post("/api/admin/teams")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"teamName", "신규팀",
							"projectManagerId", 이사.getEmployeeId(),
							"departmentId", 부서.getDepartmentId()))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.teamId").isNumber());

			초기화();
			Team 신규팀 = em.createQuery("select t from Team t where t.teamName = :name", Team.class)
					.setParameter("name", "신규팀")
					.getSingleResult();
			List<TeamManager> 담당자 = 담당자목록(신규팀.getTeamId());
			assertThat(담당자).hasSize(1);
			assertThat(담당자.get(0).getParentTeamId()).isEqualTo(상위팀번호);
		}

		@Test
		void 상위_팀을_지정하면_그_팀이_상위_팀이_된다() throws Exception {
			Team 다른팀 = 팀("상위후보팀", 부서);
			em.flush();
			Long 상위팀번호 = 다른팀.getTeamId();

			mockMvc.perform(post("/api/admin/teams")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"teamName", "신규팀",
							"projectManagerId", 이사.getEmployeeId(),
							"departmentId", 부서.getDepartmentId(),
							"parentTeamId", 상위팀번호))))
					.andExpect(status().isOk());

			초기화();
			Team 신규팀 = em.createQuery("select t from Team t where t.teamName = :name", Team.class)
					.setParameter("name", "신규팀")
					.getSingleResult();
			assertThat(담당자목록(신규팀.getTeamId()).get(0).getParentTeamId()).isEqualTo(상위팀번호);
		}

		@Test
		void 이미_존재하는_팀명이면_409를_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/teams")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"teamName", 팀.getTeamName(),
							"projectManagerId", 이사.getEmployeeId(),
							"departmentId", 부서.getDepartmentId()))))
					.andExpect(status().isConflict());
		}

		@Test
		void 담당자가_존재하지_않으면_404를_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/teams")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"teamName", "신규팀",
							"projectManagerId", 99999999L,
							"departmentId", 부서.getDepartmentId()))))
					.andExpect(status().isNotFound());
		}

		@Test
		void 소속_부서가_존재하지_않으면_400을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/teams")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"teamName", "신규팀",
							"projectManagerId", 이사.getEmployeeId(),
							"departmentId", 99999999L))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 비활성화된_부서면_400을_반환한다() throws Exception {
			Department 비활성 = 비활성부서("비활성부서");

			mockMvc.perform(post("/api/admin/teams")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"teamName", "신규팀",
							"projectManagerId", 이사.getEmployeeId(),
							"departmentId", 비활성.getDepartmentId()))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 상위_팀이_존재하지_않으면_400을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/teams")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"teamName", "신규팀",
							"projectManagerId", 이사.getEmployeeId(),
							"departmentId", 부서.getDepartmentId(),
							"parentTeamId", 99999999L))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 팀명이_비어_있으면_400을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/teams")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"teamName", "  ",
							"projectManagerId", 이사.getEmployeeId(),
							"departmentId", 부서.getDepartmentId()))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 팀명이_30자를_넘으면_400을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/teams")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"teamName", "가".repeat(31),
							"projectManagerId", 이사.getEmployeeId(),
							"departmentId", 부서.getDepartmentId()))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 담당자를_지정하지_않으면_400을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/teams")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"teamName", "신규팀",
							"departmentId", 부서.getDepartmentId()))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 소속_부서를_지정하지_않으면_400을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/teams")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"teamName", "신규팀",
							"projectManagerId", 이사.getEmployeeId()))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 사장이_아닌_관리자면_403을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/teams")
					.header("Authorization", adminBearer(이사))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"teamName", "신규팀",
							"projectManagerId", 이사.getEmployeeId(),
							"departmentId", 부서.getDepartmentId()))))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("팀 수정 PUT /api/admin/teams/{teamId}")
	class UpdateTeam {

		@Test
		void 팀명을_변경한다() throws Exception {
			Team 대상팀 = 팀("수정대상팀", 부서);
			팀관리자(대상팀, 사장, 팀);
			Long 팀번호 = 대상팀.getTeamId();

			mockMvc.perform(put("/api/admin/teams/" + 팀번호)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("teamName", "변경된팀명"))))
					.andExpect(status().isOk());

			초기화();
			assertThat(em.find(Team.class, 팀번호).getTeamName()).isEqualTo("변경된팀명");
		}

		@Test
		void 이미_존재하는_팀명으로_변경하면_409를_반환한다() throws Exception {
			Team 대상팀 = 팀("수정대상팀", 부서);
			팀관리자(대상팀, 사장, 팀);
			em.flush();

			mockMvc.perform(put("/api/admin/teams/" + 대상팀.getTeamId())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("teamName", 팀.getTeamName()))))
					.andExpect(status().isConflict());
		}

		@Test
		void 존재하지_않는_팀이면_404를_반환한다() throws Exception {
			mockMvc.perform(put("/api/admin/teams/99999999")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("teamName", "변경된팀명"))))
					.andExpect(status().isNotFound());
		}

		@Test
		void 담당자를_변경하면_기존_담당자가_교체된다() throws Exception {
			Team 대상팀 = 팀("담당자교체팀", 부서);
			팀관리자(대상팀, 사장, 팀);
			Long 팀번호 = 대상팀.getTeamId();
			Long 새담당자번호 = 이사.getEmployeeId();

			mockMvc.perform(put("/api/admin/teams/" + 팀번호)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("projectManagerId", 새담당자번호))))
					.andExpect(status().isOk());

			초기화();
			List<TeamManager> 담당자 = 담당자목록(팀번호);
			assertThat(담당자).hasSize(1);
			assertThat(담당자.get(0).getProjectManagerId()).isEqualTo(새담당자번호);
		}

		@Test
		void 담당자가_존재하지_않으면_404를_반환한다() throws Exception {
			Team 대상팀 = 팀("담당자교체팀", 부서);
			팀관리자(대상팀, 사장, 팀);
			em.flush();

			mockMvc.perform(put("/api/admin/teams/" + 대상팀.getTeamId())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("projectManagerId", 99999999L))))
					.andExpect(status().isNotFound());
		}

		@Test
		void 부서를_변경하면_소속_사원의_부서도_동기화된다() throws Exception {
			Department 새부서 = 부서("이동할부서");
			Team 대상팀 = 팀("부서이동팀", 부서);
			팀관리자(대상팀, 사장, 팀);
			Employee 소속사원 = 사원("이동대상", "사원", 부서, 대상팀, 사장, Role.EMPLOYEE, 15f);
			em.flush();
			Long 팀번호 = 대상팀.getTeamId();
			Long 새부서번호 = 새부서.getDepartmentId();
			Long 사원번호 = 소속사원.getEmployeeId();

			mockMvc.perform(put("/api/admin/teams/" + 팀번호)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentId", 새부서번호))))
					.andExpect(status().isOk());

			초기화();
			assertThat(em.find(Team.class, 팀번호).getDepartment().getDepartmentId()).isEqualTo(새부서번호);
			assertThat(em.find(Employee.class, 사원번호).getDepartment().getDepartmentId()).isEqualTo(새부서번호);
		}

		@Test
		void 존재하지_않는_부서로_변경하면_400을_반환한다() throws Exception {
			mockMvc.perform(put("/api/admin/teams/" + 팀.getTeamId())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentId", 99999999L))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 비활성화된_부서로_변경하면_400을_반환한다() throws Exception {
			Department 비활성 = 비활성부서("비활성부서");

			mockMvc.perform(put("/api/admin/teams/" + 팀.getTeamId())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentId", 비활성.getDepartmentId()))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 상위_팀이_존재하지_않으면_400을_반환한다() throws Exception {
			mockMvc.perform(put("/api/admin/teams/" + 팀.getTeamId())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("parentTeamId", 99999999L))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 자기_자신을_상위_팀으로_지정하면_400을_반환한다() throws Exception {
			mockMvc.perform(put("/api/admin/teams/" + 팀.getTeamId())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("parentTeamId", 팀.getTeamId()))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 하위_팀을_상위_팀으로_지정하면_순환_참조로_400을_반환한다() throws Exception {
			Team 상위팀 = 팀("순환상위팀", 부서);
			Team 하위팀 = 팀("순환하위팀", 부서);
			팀관리자(상위팀, 사장, 팀);
			팀관리자(하위팀, 사장, 상위팀);
			em.flush();

			mockMvc.perform(put("/api/admin/teams/" + 상위팀.getTeamId())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("parentTeamId", 하위팀.getTeamId()))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 담당자만_유지하고_상위_팀만_바꿀_수_있다() throws Exception {
			Team 대상팀 = 팀("상위팀변경팀", 부서);
			Team 새상위팀 = 팀("새상위팀", 부서);
			팀관리자(대상팀, 사장, 팀);
			em.flush();
			Long 팀번호 = 대상팀.getTeamId();
			Long 새상위팀번호 = 새상위팀.getTeamId();

			mockMvc.perform(put("/api/admin/teams/" + 팀번호)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("parentTeamId", 새상위팀번호))))
					.andExpect(status().isOk());

			초기화();
			List<TeamManager> 담당자 = 담당자목록(팀번호);
			assertThat(담당자).hasSize(1);
			assertThat(담당자.get(0).getParentTeamId()).isEqualTo(새상위팀번호);
		}

		@Test
		void 담당자_정보가_없는_팀의_담당자만_지정하면_400을_반환한다() throws Exception {
			// 상위 팀 정보를 알 수 없어 결재선을 만들 수 없으므로 거부한다.
			Team 담당자없는팀 = 팀("담당자없는팀", 부서);
			em.flush();

			mockMvc.perform(put("/api/admin/teams/" + 담당자없는팀.getTeamId())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("projectManagerId", 이사.getEmployeeId()))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 아무_값도_보내지_않으면_기존_정보를_유지한다() throws Exception {
			Long 팀번호 = 팀.getTeamId();

			mockMvc.perform(put("/api/admin/teams/" + 팀번호)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(new HashMap<String, Object>())))
					.andExpect(status().isOk());

			초기화();
			assertThat(em.find(Team.class, 팀번호).getTeamName()).isEqualTo("팀관리테스트팀");
			assertThat(담당자목록(팀번호)).hasSize(1);
		}

		@Test
		void 사장이_아닌_관리자면_403을_반환한다() throws Exception {
			mockMvc.perform(put("/api/admin/teams/" + 팀.getTeamId())
					.header("Authorization", adminBearer(이사))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("teamName", "변경된팀명"))))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("팀 삭제 DELETE /api/admin/teams/{teamId}")
	class DeleteTeam {

		@Test
		void 하위_팀과_소속_사원이_없으면_비활성화되고_결재선도_지워진다() throws Exception {
			Team 삭제될팀 = 팀("삭제될팀", 부서);
			팀관리자(삭제될팀, 사장, 팀);
			Long 팀번호 = 삭제될팀.getTeamId();

			mockMvc.perform(delete("/api/admin/teams/" + 팀번호)
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isOk());

			초기화();
			assertThat(em.find(Team.class, 팀번호).getEnabled()).isFalse();
			assertThat(담당자목록(팀번호)).isEmpty();
		}

		@Test
		void 존재하지_않는_팀이면_404를_반환한다() throws Exception {
			mockMvc.perform(delete("/api/admin/teams/99999999")
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isNotFound());
		}

		@Test
		void 하위_팀이_있으면_400을_반환한다() throws Exception {
			Team 상위팀 = 팀("삭제대상상위팀", 부서);
			Team 하위팀 = 팀("삭제대상하위팀", 부서);
			팀관리자(하위팀, 사장, 상위팀);
			em.flush();

			mockMvc.perform(delete("/api/admin/teams/" + 상위팀.getTeamId())
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 소속_사원이_있으면_400을_반환한다() throws Exception {
			mockMvc.perform(delete("/api/admin/teams/" + 팀.getTeamId())
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 이미_비활성화된_팀이면_200을_반환한다() throws Exception {
			// 멱등 처리: 이미 삭제된 팀은 그대로 두고 성공으로 응답한다.
			Team 비활성 = 비활성팀("이미삭제된팀", 부서);
			Long 팀번호 = 비활성.getTeamId();

			mockMvc.perform(delete("/api/admin/teams/" + 팀번호)
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isOk());

			초기화();
			assertThat(em.find(Team.class, 팀번호).getEnabled()).isFalse();
		}

		@Test
		void 사장이_아닌_관리자면_403을_반환한다() throws Exception {
			mockMvc.perform(delete("/api/admin/teams/" + 팀.getTeamId())
					.header("Authorization", adminBearer(이사)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 토큰이_없으면_403을_반환한다() throws Exception {
			mockMvc.perform(delete("/api/admin/teams/" + 팀.getTeamId()))
					.andExpect(status().isForbidden());
		}
	}
}
