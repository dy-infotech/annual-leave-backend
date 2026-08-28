package com.dyinfotech.annualleavebackend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * 관리자 사원 API(/api/admin/employees) 통합 테스트.
 *
 * <p>조회는 팀 관리자면 가능하지만 수정은 인사권(사장 직급)까지 필요하다.
 * 수정 엔드포인트에는 {@code @Valid}가 없어 DTO 제약이 동작하지 않는다.
 */
@DisplayName("관리자 사원 API")
class AdminEmployeeApiTest extends IntegrationTestSupport {

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
	private Employee 대상사원;
	private Employee 일반사원;

	@BeforeEach
	void setUpOrganization() {
		// 캐시는 애플리케이션 컨텍스트 단위로 공유되므로 이전 테스트의 롤백된 데이터가 남지 않도록 비운다.
		clearCaches();

		부서 = 부서("사원관리테스트부서");
		팀 = 팀("사원관리테스트팀", 부서);
		하위팀 = 팀("사원관리하위팀", 부서);
		사장 = 사원("사장님", "사장", 부서, 팀, null, Role.ADMIN, 15f);
		팀관리자(팀, 사장, 팀);
		// 팀 관리자이지만 인사권(사장 직급)은 없는 관리자
		팀장 = 사원("팀장님", "이사", 부서, 하위팀, 사장, Role.ADMIN, 15f);
		팀관리자(하위팀, 팀장, 팀);
		대상사원 = 사원("김특이한이름", "사원", 부서, 팀, 사장, Role.EMPLOYEE, 15f);
		일반사원 = 사원("평사원", "사원", 부서, 팀, 사장, Role.EMPLOYEE, 15f);
		em.flush();
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

	private Employee 사번으로조회(String employeeNumber) {
		return em.createQuery("select e from Employee e where e.employeeNumber = :number", Employee.class)
				.setParameter("number", employeeNumber)
				.getSingleResult();
	}

	private Map<String, Object> 수정요청(String name) {
		Map<String, Object> request = new HashMap<>();
		request.put("name", name);
		request.put("email", "changed@example.com");
		request.put("department", 부서.getDepartmentName());
		request.put("hireDate", "2020-03-02");
		return request;
	}

	@Nested
	@DisplayName("전체 사원 조회 GET /api/admin/employees/all")
	class GetAllEmployees {

		@Test
		void 검색어가_없으면_전체_사원을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/employees/all")
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[?(@.employeeNumber=='" + 대상사원.getEmployeeNumber() + "')]").exists())
					.andExpect(jsonPath("$[?(@.employeeNumber=='" + 대상사원.getEmployeeNumber() + "')].name")
							.value("김특이한이름"));
		}

		@Test
		void 사번_일부로_검색한다() throws Exception {
			String 사번 = 대상사원.getEmployeeNumber();

			mockMvc.perform(get("/api/admin/employees/all")
					.header("Authorization", adminBearer(사장))
					.param("searchParam", 사번.substring(1)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].employeeNumber").value(사번));
		}

		@Test
		void 이름_일부로_검색한다() throws Exception {
			mockMvc.perform(get("/api/admin/employees/all")
					.header("Authorization", adminBearer(사장))
					.param("searchParam", "특이한"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].name").value("김특이한이름"));
		}

		@Test
		void 일치하는_사원이_없으면_빈_목록을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/employees/all")
					.header("Authorization", adminBearer(사장))
					.param("searchParam", "존재할리없는검색어"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(0));
		}

		@Test
		void 인사권이_없어도_팀_관리자면_조회할_수_있다() throws Exception {
			// 조회는 checkAdmin(팀 관리자 여부)만 확인한다.
			mockMvc.perform(get("/api/admin/employees/all")
					.header("Authorization", adminBearer(팀장)))
					.andExpect(status().isOk());
		}

		@Test
		void 팀_관리자가_아니면_403을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/employees/all")
					.header("Authorization", adminBearer(대상사원)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 일반_사원_토큰이면_403을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/employees/all")
					.header("Authorization", bearer(일반사원)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 토큰이_없으면_403을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/employees/all"))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("사원 정보 수정 PUT /api/admin/employees/{employeeNumber}")
	class UpdateEmployee {

		@Test
		void 이름과_이메일과_직급을_수정한다() throws Exception {
			String 사번 = 대상사원.getEmployeeNumber();
			Map<String, Object> 요청 = 수정요청("바뀐이름");
			요청.put("position", "주임");

			mockMvc.perform(put("/api/admin/employees/" + 사번)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isOk());

			초기화();
			Employee 수정된사원 = 사번으로조회(사번);
			assertThat(수정된사원.getName()).isEqualTo("바뀐이름");
			assertThat(수정된사원.getEmail()).isEqualTo("changed@example.com");
			assertThat(수정된사원.getPosition()).isEqualTo("주임");
		}

		@Test
		void 팀을_변경하면_부서도_함께_동기화된다() throws Exception {
			Department 새부서 = 부서("사원이동부서");
			Team 새팀 = 팀("사원이동팀", 새부서);
			em.flush();
			String 사번 = 대상사원.getEmployeeNumber();
			Long 새부서번호 = 새부서.getDepartmentId();
			Long 새팀번호 = 새팀.getTeamId();

			Map<String, Object> 요청 = 수정요청("김특이한이름");
			// 부서는 기존 부서를 보내도 팀의 소속 부서가 우선한다.
			요청.put("team", "사원이동팀");

			mockMvc.perform(put("/api/admin/employees/" + 사번)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isOk());

			초기화();
			Employee 수정된사원 = 사번으로조회(사번);
			assertThat(수정된사원.getTeam().getTeamId()).isEqualTo(새팀번호);
			assertThat(수정된사원.getDepartment().getDepartmentId()).isEqualTo(새부서번호);
		}

		@Test
		void 존재하지_않는_팀명을_보내면_기존_팀을_유지한다() throws Exception {
			String 사번 = 대상사원.getEmployeeNumber();
			Long 기존팀번호 = 팀.getTeamId();

			Map<String, Object> 요청 = 수정요청("김특이한이름");
			요청.put("team", "존재하지않는팀");

			mockMvc.perform(put("/api/admin/employees/" + 사번)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isOk());

			초기화();
			assertThat(사번으로조회(사번).getTeam().getTeamId()).isEqualTo(기존팀번호);
		}

		@Test
		void 존재하지_않는_사번이면_404를_반환한다() throws Exception {
			mockMvc.perform(put("/api/admin/employees/NOPE9999")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(수정요청("바뀐이름"))))
					.andExpect(status().isNotFound());
		}

		@Test
		void 관리하지_않는_팀으로_역할_변경을_요청하면_400을_반환한다() throws Exception {
			Map<String, Object> 요청 = 수정요청("김특이한이름");
			요청.put("targetTeamsForRoleSwap", List.of("존재하지않는관리팀"));

			mockMvc.perform(put("/api/admin/employees/" + 대상사원.getEmployeeNumber())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 관리하는_팀을_다시_요청하면_관리자에서_해제된다() throws Exception {
			// 팀에 관리자가 사장과 대상사원 둘이므로 한 명은 해제할 수 있다.
			팀관리자(팀, 대상사원, 팀);
			초기화();
			Employee 관리자로등록된사원 = 사번으로조회(대상사원.getEmployeeNumber());
			assertThat(관리자로등록된사원.getTeams()).hasSize(1);
			clearCaches();

			Map<String, Object> 요청 = 수정요청("김특이한이름");
			요청.put("targetTeamsForRoleSwap", List.of(팀.getTeamName()));

			mockMvc.perform(put("/api/admin/employees/" + 대상사원.getEmployeeNumber())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isOk());

			초기화();
			assertThat(사번으로조회(대상사원.getEmployeeNumber()).getTeams()).isEmpty();
		}

		@Test
		void 관리하지_않는_팀을_요청하면_관리자로_등록된다() throws Exception {
			String 사번 = 대상사원.getEmployeeNumber();
			Map<String, Object> 요청 = 수정요청("김특이한이름");
			요청.put("targetTeamsForRoleSwap", List.of(하위팀.getTeamName()));

			mockMvc.perform(put("/api/admin/employees/" + 사번)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isOk());

			초기화();
			assertThat(사번으로조회(사번).getTeams())
					.extracting(관리팀 -> 관리팀.getTeam().getTeamName())
					.containsExactly(하위팀.getTeamName());
		}

		@Test
		void 팀의_마지막_관리자는_해제할_수_없다() throws Exception {
			// 하위팀의 유일한 관리자인 팀장을 해제하려 하면 거부된다.
			// 관리자가 없는 팀은 소속 사원의 결재선을 만들 수 없다.
			// 갓 persist한 Employee의 관리 팀 컬렉션은 빈 상태로 잡혀 있어 다시 읽게 한다.
			초기화();
			clearCaches();
			Map<String, Object> 요청 = 수정요청("팀장님");
			요청.put("targetTeamsForRoleSwap", List.of(하위팀.getTeamName()));

			mockMvc.perform(put("/api/admin/employees/" + 팀장.getEmployeeNumber())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message")
							.value(org.hamcrest.Matchers.containsString("마지막 관리자는 해제할 수 없습니다")));

			초기화();
			assertThat(사번으로조회(팀장.getEmployeeNumber()).getTeams()).hasSize(1);
		}

		@Test
		void 인사권이_없는_관리자면_404를_반환한다() throws Exception {
			// 명세상으로는 권한 부족이므로 403이 어울리지만,
			// EmployeeService.updateEmployeeByAdmin이 NOT_FOUND로 던지고 있어 404가 나간다.
			mockMvc.perform(put("/api/admin/employees/" + 대상사원.getEmployeeNumber())
					.header("Authorization", adminBearer(팀장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(수정요청("바뀐이름"))))
					.andExpect(status().isNotFound());
		}

		@Test
		void 입사일이_없으면_500을_반환한다() throws Exception {
			// 이 엔드포인트에는 @Valid가 없어 hireDate의 @NotNull이 동작하지 않는다.
			// 그대로 연차 계산으로 넘어가 NullPointerException이 나고 500으로 응답한다.
			Map<String, Object> 요청 = new HashMap<>();
			요청.put("name", "바뀐이름");
			요청.put("email", "changed@example.com");
			요청.put("department", 부서.getDepartmentName());

			mockMvc.perform(put("/api/admin/employees/" + 대상사원.getEmployeeNumber())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isInternalServerError());
		}

		@Test
		void 이메일_형식이_잘못돼도_그대로_저장된다() throws Exception {
			// @Valid가 없어 @Email 제약이 동작하지 않는다.
			String 사번 = 대상사원.getEmployeeNumber();
			Map<String, Object> 요청 = 수정요청("김특이한이름");
			요청.put("email", "이메일아님");

			mockMvc.perform(put("/api/admin/employees/" + 사번)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(요청)))
					.andExpect(status().isOk());

			초기화();
			assertThat(사번으로조회(사번).getEmail()).isEqualTo("이메일아님");
		}

		@Test
		void 팀_관리자가_아니면_403을_반환한다() throws Exception {
			mockMvc.perform(put("/api/admin/employees/" + 대상사원.getEmployeeNumber())
					.header("Authorization", adminBearer(대상사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(수정요청("바뀐이름"))))
					.andExpect(status().isForbidden());
		}

		@Test
		void 일반_사원_토큰이면_403을_반환한다() throws Exception {
			mockMvc.perform(put("/api/admin/employees/" + 대상사원.getEmployeeNumber())
					.header("Authorization", bearer(일반사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(수정요청("바뀐이름"))))
					.andExpect(status().isForbidden());
		}

		@Test
		void 토큰이_없으면_403을_반환한다() throws Exception {
			mockMvc.perform(put("/api/admin/employees/" + 대상사원.getEmployeeNumber())
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(수정요청("바뀐이름"))))
					.andExpect(status().isForbidden());
		}
	}
}
