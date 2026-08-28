package com.dyinfotech.annualleavebackend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.dyinfotech.annualleavebackend.common.type.DepartmentType;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.support.IntegrationTestSupport;
import com.github.benmanes.caffeine.cache.LoadingCache;

/**
 * 관리자 인증 API(/api/admin/auth) 통합 테스트.
 *
 * <p>사원 등록은 부서/직급/팀 관리 권한을 각각 검증한다.
 */
@DisplayName("관리자 인증 API")
class AdminAuthApiTest extends IntegrationTestSupport {

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
	private Team 하위팀;
	private Employee 사장;
	private Employee 팀장;
	private Employee 일반사원;

	@BeforeEach
	void setUpOrganization() {
		// 캐시는 애플리케이션 컨텍스트 단위로 공유되므로 이전 테스트의 롤백된 데이터가 남지 않도록 비운다.
		clearCaches();

		부서 = 부서("인증관리테스트부서");
		팀 = 팀("인증관리테스트팀", 부서);
		하위팀 = 팀("인증관리하위팀", 부서);
		사장 = 사원("사장님", "사장", 부서, 팀, null, Role.ADMIN, 15f);
		팀관리자(팀, 사장, 팀);
		// 하위팀만 관리하는, 인사권 없는 관리자
		팀장 = 사원("팀장님", "이사", 부서, 하위팀, 사장, Role.ADMIN, 15f);
		팀관리자(하위팀, 팀장, 팀);
		일반사원 = 사원("평사원", "사원", 부서, 팀, 사장, Role.EMPLOYEE, 15f);

		// 갓 persist된 Employee의 teams 컬렉션은 빈 상태로 초기화되어 있어 DB를 다시 읽지 않는다.
		// 관리 팀 목록(getTeams)을 쓰는 API가 있으므로 영속성 컨텍스트를 비우고 다시 읽어 온다.
		재조회();
	}

	private void 재조회() {
		Long 부서번호 = 부서.getDepartmentId();
		Long 팀번호 = 팀.getTeamId();
		Long 하위팀번호 = 하위팀.getTeamId();
		Long 사장번호 = 사장.getEmployeeId();
		Long 팀장번호 = 팀장.getEmployeeId();
		Long 일반사원번호 = 일반사원.getEmployeeId();
		초기화();
		부서 = em.find(Department.class, 부서번호);
		팀 = em.find(Team.class, 팀번호);
		하위팀 = em.find(Team.class, 하위팀번호);
		사장 = em.find(Employee.class, 사장번호);
		팀장 = em.find(Employee.class, 팀장번호);
		일반사원 = em.find(Employee.class, 일반사원번호);
	}

	private void clearCaches() {
		departmentCache.invalidateAll();
		teamCache.invalidateAll();
		teamManagerCache.invalidateAll();
		cacheManager.getCacheNames()
				.forEach(name -> cacheManager.getCache(name).clear());
	}

	private void 초기화() {
		em.flush();
		em.clear();
	}

	private Department 대표이사부서() {
		String name = DepartmentType.getParentDepartmentType().getName();
		return em.createQuery("select d from Department d where d.departmentName = :name", Department.class)
				.setParameter("name", name)
				.getResultStream()
				.findFirst()
				.orElseGet(() -> 부서(name));
	}

	private long 부서수(String name) {
		return em.createQuery("select count(d) from Department d where d.departmentName = :name", Long.class)
				.setParameter("name", name)
				.getSingleResult();
	}

	private long 팀수(String name) {
		return em.createQuery("select count(t) from Team t where t.teamName = :name", Long.class)
				.setParameter("name", name)
				.getSingleResult();
	}

	/** 사원 등록 요청 기본값. 필요한 항목만 덮어써서 쓴다. */
	private Map<String, Object> 등록요청() {
		Map<String, Object> request = new HashMap<>();
		request.put("employeeNumber", "R" + System.nanoTime() % 10_000_000L);
		request.put("name", "신입사원");
		request.put("department", 부서.getDepartmentName());
		request.put("team", 팀.getTeamName());
		request.put("position", "사원");
		request.put("email", "newbie@example.com");
		request.put("hireDate", "2024-03-04");
		return request;
	}

	@Nested
	@DisplayName("부서/팀/직급 조회 GET /api/admin/auth/common")
	class GetCommonData {

		@Test
		void 사장이면_자기보다_낮은_직급만_내려온다() throws Exception {
			mockMvc.perform(get("/api/admin/auth/common")
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.position.length()").value(10))
					.andExpect(jsonPath("$.position[?(@=='사장')]").doesNotExist())
					.andExpect(jsonPath("$.position[?(@=='전무')]").exists());
		}

		@Test
		void 이사면_부장_이하_직급만_내려온다() throws Exception {
			mockMvc.perform(get("/api/admin/auth/common")
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.position.length()").value(7))
					.andExpect(jsonPath("$.position[?(@=='부장')]").exists())
					.andExpect(jsonPath("$.position[?(@=='이사')]").doesNotExist());
		}

		@Test
		void 활성_부서_목록을_함께_내려준다() throws Exception {
			mockMvc.perform(get("/api/admin/auth/common")
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.department[?(@=='인증관리테스트부서')]").exists());
		}

		@Test
		void 관리_중인_팀과_그_하위_팀이_내려온다() throws Exception {
			mockMvc.perform(get("/api/admin/auth/common")
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.accessibleTeam[?(@=='인증관리테스트팀')]").exists())
					.andExpect(jsonPath("$.accessibleTeam[?(@=='인증관리하위팀')]").exists());
		}

		@Test
		void 하위_팀만_관리하면_자기_팀만_내려온다() throws Exception {
			mockMvc.perform(get("/api/admin/auth/common")
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.accessibleTeam[?(@=='인증관리하위팀')]").exists())
					.andExpect(jsonPath("$.accessibleTeam[?(@=='인증관리테스트팀')]").doesNotExist());
		}

		@Test
		void 직급_문자열이_enum에_없으면_500을_반환한다() throws Exception {
			// PositionType.getType이 null을 돌려주는데 그대로 ordinal()을 호출해
			// NullPointerException이 나고 500으로 응답한다. (400이 어울리는 자리)
			Employee 이상직급자 = 사원("이상직급", "없는직급", 부서, 팀, 사장, Role.ADMIN, 15f);
			팀관리자(팀, 이상직급자, 팀);
			em.flush();

			mockMvc.perform(get("/api/admin/auth/common")
					.header("Authorization", adminBearer(이상직급자)))
					.andExpect(status().isInternalServerError());
		}

		@Test
		void 팀_관리자가_아니면_403을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/auth/common")
					.header("Authorization", adminBearer(일반사원)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 일반_사원_토큰이면_403을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/auth/common")
					.header("Authorization", bearer(일반사원)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 토큰이_없으면_403을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/auth/common"))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("부서/팀 등록 POST /api/admin/auth/common")
	class SetCommonData {

		@Test
		void JSON_바디는_무시되어_아무것도_등록되지_않는다() throws Exception {
			// 컨트롤러 파라미터에 @RequestBody가 없어 JSON 본문이 바인딩되지 않는다.
			mockMvc.perform(post("/api/admin/auth/common")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("department", "바디부서"))))
					.andExpect(status().isOk());

			초기화();
			assertThat(부서수("바디부서")).isZero();
		}

		@Test
		void 쿼리_파라미터로_보내도_아무것도_등록되지_않는다() throws Exception {
			// @ModelAttribute로 바인딩되지만 OrganizationInfoRequest에 setter가 없어
			// department/team이 항상 null이다. 결국 이 엔드포인트는 무동작이다.
			mockMvc.perform(post("/api/admin/auth/common")
					.header("Authorization", adminBearer(사장))
					.param("department", "파라미터부서")
					.param("team", "파라미터팀"))
					.andExpect(status().isOk());

			초기화();
			assertThat(부서수("파라미터부서")).isZero();
			assertThat(팀수("파라미터팀")).isZero();
		}

		@Test
		void 팀만_보내도_400이_아니라_200을_반환한다() throws Exception {
			// 소속 부서 없이 팀만 등록하면 400이어야 하지만, 바인딩 자체가 되지 않아
			// 검증 분기까지 도달하지 못하고 200이 나간다.
			mockMvc.perform(post("/api/admin/auth/common")
					.header("Authorization", adminBearer(사장))
					.param("team", "부서없는팀"))
					.andExpect(status().isOk());

			초기화();
			assertThat(팀수("부서없는팀")).isZero();
		}

		@Test
		void 팀_관리자가_아니면_403을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/auth/common")
					.header("Authorization", adminBearer(일반사원))
					.param("department", "파라미터부서"))
					.andExpect(status().isForbidden());
		}

		@Test
		void 일반_사원_토큰이면_403을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/auth/common")
					.header("Authorization", bearer(일반사원))
					.param("department", "파라미터부서"))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("사원 등록 POST /api/admin/auth/register")
	class RegisterEmployee {

		@Test
		void 사장이_기존_팀에_사원을_등록한다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			String 사번 = (String) 요청.get("employeeNumber");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.employeeId").isNumber())
					.andExpect(jsonPath("$.employeeNumber").value(사번));

			초기화();
			Employee 등록된사원 = em.createQuery(
					"select e from Employee e where e.employeeNumber = :number", Employee.class)
					.setParameter("number", 사번)
					.getSingleResult();
			assertThat(등록된사원.getName()).isEqualTo("신입사원");
			assertThat(등록된사원.getTeam().getTeamName()).isEqualTo("인증관리테스트팀");
		}

		@Test
		void 요청_부서가_팀의_소속_부서와_다르면_팀의_부서로_저장한다() throws Exception {
			부서("다른부서");
			em.flush();
			Map<String, Object> 요청 = 등록요청();
			요청.put("department", "다른부서");
			String 사번 = (String) 요청.get("employeeNumber");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isOk());

			초기화();
			Employee 등록된사원 = em.createQuery(
					"select e from Employee e where e.employeeNumber = :number", Employee.class)
					.setParameter("number", 사번)
					.getSingleResult();
			assertThat(등록된사원.getDepartment().getDepartmentName()).isEqualTo("인증관리테스트부서");
		}

		@Test
		void 팀_관리자가_아니어도_등록할_수_있다() throws Exception {
			// 컨트롤러의 checkAdmin 호출이 주석 처리되어 있어 팀 관리자 여부를 확인하지 않는다.
			// (아래 사장은 team_manager에 등록되지 않았지만 대표이사라서 모든 팀의 관리자로 인정된다)
			Employee 관리자아닌사장 = 사원("등록만하는사장", "사장", 부서, 팀, 사장, Role.ADMIN, 15f);
			em.flush();

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(관리자아닌사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(등록요청())))
					.andExpect(status().isOk());
		}

		@Test
		void 대표이사_부서는_사장만_등록할_수_있다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("department", 대표이사부서().getDepartmentName());

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 승인자와_다른_부서는_등록할_수_없다() throws Exception {
			부서("남의부서");
			em.flush();
			Map<String, Object> 요청 = 등록요청();
			요청.put("department", "남의부서");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 존재하지_않는_부서면_400을_반환한다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("department", "없는부서");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 자기와_동등한_직급은_등록할_수_없다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("position", "사장");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 자기보다_높은_직급은_등록할_수_없다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("team", 하위팀.getTeamName());
			요청.put("position", "전무");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 직급_문자열이_enum에_없으면_500을_반환한다() throws Exception {
			// PositionType.getType이 null을 돌려주는데 Enum.compareTo(null)로 넘어가
			// NullPointerException이 나고 500으로 응답한다. (400이 어울리는 자리)
			Map<String, Object> 요청 = 등록요청();
			요청.put("position", "없는직급");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isInternalServerError());
		}

		@Test
		void 관리하지_않는_팀에는_등록할_수_없다() throws Exception {
			Team 남의팀 = 팀("남의팀", 부서);
			팀관리자(남의팀, 사장, 팀);
			em.flush();

			Map<String, Object> 요청 = 등록요청();
			요청.put("team", 남의팀.getTeamName());

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 상위_팀_관리자는_하위_팀에도_등록할_수_있다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("team", 하위팀.getTeamName());
			String 사번 = (String) 요청.get("employeeNumber");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isOk());

			초기화();
			Employee 등록된사원 = em.createQuery(
					"select e from Employee e where e.employeeNumber = :number", Employee.class)
					.setParameter("number", 사번)
					.getSingleResult();
			assertThat(등록된사원.getTeam().getTeamName()).isEqualTo("인증관리하위팀");
		}

		@Test
		void 신규_팀은_사장이_아니면_만들_수_없다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("team", "완전새로운팀");
			요청.put("role", "ADMIN");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isForbidden());

			초기화();
			assertThat(팀수("완전새로운팀")).isZero();
		}

		@Test
		void 신규_팀에는_관리자부터_등록해야_한다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("team", "완전새로운팀");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 사장이_신규_팀에_관리자를_등록하면_팀과_결재선이_함께_만들어진다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("team", "완전새로운팀");
			요청.put("role", "ADMIN");
			String 사번 = (String) 요청.get("employeeNumber");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isOk());

			초기화();
			assertThat(팀수("완전새로운팀")).isEqualTo(1L);
			Employee 등록된사원 = em.createQuery(
					"select e from Employee e where e.employeeNumber = :number", Employee.class)
					.setParameter("number", 사번)
					.getSingleResult();
			List<TeamManager> 담당자 = em.createQuery(
					"select tm from TeamManager tm where tm.team.teamName = :name", TeamManager.class)
					.setParameter("name", "완전새로운팀")
					.getResultList();
			assertThat(담당자).hasSize(1);
			assertThat(담당자.get(0).getProjectManagerId()).isEqualTo(등록된사원.getEmployeeId());
			assertThat(담당자.get(0).getParentTeam().getTeamName()).isEqualTo("인증관리테스트팀");
		}

		@Test
		void 관리자_역할_등록은_인사권이_있어야_한다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("team", 하위팀.getTeamName());
			요청.put("role", "ADMIN");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 사번이_비어_있으면_400을_반환한다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("employeeNumber", "");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 이름이_비어_있으면_400을_반환한다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("name", "  ");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 팀이_비어_있으면_400을_반환한다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("team", "");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 입사일이_비어_있으면_400을_반환한다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("hireDate", "");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 입사일_형식이_잘못되면_400을_반환한다() throws Exception {
			Map<String, Object> 요청 = 등록요청();
			요청.put("hireDate", "2024/03/04");

			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 일반_사원_토큰이면_403을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/auth/register")
					.header("Authorization", bearer(일반사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(등록요청())))
					.andExpect(status().isForbidden());
		}

		@Test
		void 토큰이_없으면_403을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(등록요청())))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("FCM 토큰 등록 POST /api/admin/auth/sync-fcm-token")
	class SyncFcmToken {

		@Test
		void 관리자면_알림_서비스에_토큰_동기화를_위임한다() throws Exception {
			mockMvc.perform(post("/api/admin/auth/sync-fcm-token")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"fcmToken", "test-fcm-token",
							"deviceOs", "android"))))
					.andExpect(status().isOk());

			verify(notificationService).syncToken(사장.getEmployeeId(), "test-fcm-token", "android");
		}

		@Test
		void FCM_토큰이_비어_있으면_400을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/auth/sync-fcm-token")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"fcmToken", "",
							"deviceOs", "android"))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 디바이스_정보가_비어_있으면_400을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/auth/sync-fcm-token")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"fcmToken", "test-fcm-token",
							"deviceOs", ""))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 팀_관리자가_아니면_403을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/auth/sync-fcm-token")
					.header("Authorization", adminBearer(일반사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"fcmToken", "test-fcm-token",
							"deviceOs", "android"))))
					.andExpect(status().isForbidden());
		}

		@Test
		void 토큰이_없으면_403을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/auth/sync-fcm-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"fcmToken", "test-fcm-token",
							"deviceOs", "android"))))
					.andExpect(status().isForbidden());
		}
	}
}
