package com.dyinfotech.annualleavebackend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * 관리자 부서 API(/api/admin/departments) 통합 테스트.
 *
 * <p>모든 엔드포인트가 인사권(사장 직급)을 요구한다.
 */
@DisplayName("관리자 부서 API")
class AdminDepartmentApiTest extends IntegrationTestSupport {

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

		부서 = 부서("부서관리테스트부서");
		팀 = 팀("부서관리테스트팀", 부서);
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

	/** 시드에 있는 대표이사 부서를 가져온다. 없으면 만든다. */
	private Department 대표이사부서() {
		String name = DepartmentType.getParentDepartmentType().getName();
		return em.createQuery("select d from Department d where d.departmentName = :name", Department.class)
				.setParameter("name", name)
				.getResultStream()
				.findFirst()
				.orElseGet(() -> 부서(name));
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

	private Department 다시조회(Long departmentId) {
		em.flush();
		em.clear();
		return em.find(Department.class, departmentId);
	}

	@Nested
	@DisplayName("부서 목록 조회 GET /api/admin/departments")
	class GetDepartments {

		@Test
		void 사장이면_활성_부서_목록을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/departments")
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[?(@.departmentName=='부서관리테스트부서')]").exists());
		}

		@Test
		void 비활성_부서는_목록에서_빠진다() throws Exception {
			비활성부서("비활성부서");

			mockMvc.perform(get("/api/admin/departments")
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[?(@.departmentName=='비활성부서')]").doesNotExist());
		}

		@Test
		void 사장이_아닌_관리자면_403을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/departments")
					.header("Authorization", adminBearer(이사)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 일반_사원_토큰이면_403을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/departments")
					.header("Authorization", bearer(일반사원)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 토큰이_없으면_403을_반환한다() throws Exception {
			mockMvc.perform(get("/api/admin/departments"))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("부서 등록 POST /api/admin/departments")
	class CreateDepartment {

		@Test
		void 새로운_부서명이면_등록에_성공한다() throws Exception {
			mockMvc.perform(post("/api/admin/departments")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", "신규부서"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.departmentId").isNumber());

			em.flush();
			assertThat(em.createQuery("select count(d) from Department d where d.departmentName = :name", Long.class)
					.setParameter("name", "신규부서")
					.getSingleResult()).isEqualTo(1L);
		}

		@Test
		void 앞뒤_공백은_제거하고_저장한다() throws Exception {
			mockMvc.perform(post("/api/admin/departments")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", "  공백부서  "))))
					.andExpect(status().isOk());

			em.flush();
			assertThat(em.createQuery("select count(d) from Department d where d.departmentName = :name", Long.class)
					.setParameter("name", "공백부서")
					.getSingleResult()).isEqualTo(1L);
		}

		@Test
		void 이미_존재하는_부서명이면_409를_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/departments")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", 부서.getDepartmentName()))))
					.andExpect(status().isConflict());
		}

		@Test
		void 부서명이_비어_있으면_400을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/departments")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", "   "))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 부서명이_50자를_넘으면_400을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/departments")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", "가".repeat(51)))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 사장이_아닌_관리자면_403을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/departments")
					.header("Authorization", adminBearer(이사))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", "신규부서"))))
					.andExpect(status().isForbidden());
		}

		@Test
		void 일반_사원_토큰이면_403을_반환한다() throws Exception {
			mockMvc.perform(post("/api/admin/departments")
					.header("Authorization", bearer(일반사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", "신규부서"))))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("부서 수정 PUT /api/admin/departments/{departmentId}")
	class UpdateDepartment {

		@Test
		void 부서명을_변경한다() throws Exception {
			Long 부서번호 = 부서.getDepartmentId();

			mockMvc.perform(put("/api/admin/departments/" + 부서번호)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", "변경된부서명"))))
					.andExpect(status().isOk());

			assertThat(다시조회(부서번호).getDepartmentName()).isEqualTo("변경된부서명");
		}

		@Test
		void 같은_이름으로_변경하면_아무것도_바뀌지_않고_200을_반환한다() throws Exception {
			Long 부서번호 = 부서.getDepartmentId();

			mockMvc.perform(put("/api/admin/departments/" + 부서번호)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", "부서관리테스트부서"))))
					.andExpect(status().isOk());

			assertThat(다시조회(부서번호).getDepartmentName()).isEqualTo("부서관리테스트부서");
		}

		@Test
		void 존재하지_않는_부서면_404를_반환한다() throws Exception {
			mockMvc.perform(put("/api/admin/departments/99999999")
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", "변경된부서명"))))
					.andExpect(status().isNotFound());
		}

		@Test
		void 대표이사_부서명은_변경할_수_없다() throws Exception {
			Long 대표이사부서번호 = 대표이사부서().getDepartmentId();

			mockMvc.perform(put("/api/admin/departments/" + 대표이사부서번호)
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", "바뀐대표이사"))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 다른_부서와_이름이_겹치면_409를_반환한다() throws Exception {
			Department 다른부서 = 부서("다른부서");
			em.flush();

			mockMvc.perform(put("/api/admin/departments/" + 다른부서.getDepartmentId())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", "부서관리테스트부서"))))
					.andExpect(status().isConflict());
		}

		@Test
		void 부서명이_비어_있으면_400을_반환한다() throws Exception {
			mockMvc.perform(put("/api/admin/departments/" + 부서.getDepartmentId())
					.header("Authorization", adminBearer(사장))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", ""))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 사장이_아닌_관리자면_403을_반환한다() throws Exception {
			mockMvc.perform(put("/api/admin/departments/" + 부서.getDepartmentId())
					.header("Authorization", adminBearer(이사))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("departmentName", "변경된부서명"))))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("부서 삭제 DELETE /api/admin/departments/{departmentId}")
	class DeleteDepartment {

		@Test
		void 소속_팀이_없으면_비활성화된다() throws Exception {
			Department 빈부서 = 부서("소속팀없는부서");
			em.flush();
			Long 부서번호 = 빈부서.getDepartmentId();

			mockMvc.perform(delete("/api/admin/departments/" + 부서번호)
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isOk());

			assertThat(다시조회(부서번호).getEnabled()).isFalse();
		}

		@Test
		void 존재하지_않는_부서면_404를_반환한다() throws Exception {
			mockMvc.perform(delete("/api/admin/departments/99999999")
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isNotFound());
		}

		@Test
		void 대표이사_부서는_삭제할_수_없다() throws Exception {
			Long 대표이사부서번호 = 대표이사부서().getDepartmentId();

			mockMvc.perform(delete("/api/admin/departments/" + 대표이사부서번호)
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 소속_활성_팀이_있으면_400을_반환한다() throws Exception {
			mockMvc.perform(delete("/api/admin/departments/" + 부서.getDepartmentId())
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 이미_비활성화된_부서면_200을_반환한다() throws Exception {
			// 멱등 처리: 이미 삭제된 부서는 그대로 두고 성공으로 응답한다.
			Department 비활성 = 비활성부서("이미삭제된부서");
			Long 부서번호 = 비활성.getDepartmentId();

			mockMvc.perform(delete("/api/admin/departments/" + 부서번호)
					.header("Authorization", adminBearer(사장)))
					.andExpect(status().isOk());

			assertThat(다시조회(부서번호).getEnabled()).isFalse();
		}

		@Test
		void 사장이_아닌_관리자면_403을_반환한다() throws Exception {
			mockMvc.perform(delete("/api/admin/departments/" + 부서.getDepartmentId())
					.header("Authorization", adminBearer(이사)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 토큰이_없으면_403을_반환한다() throws Exception {
			mockMvc.perform(delete("/api/admin/departments/" + 부서.getDepartmentId()))
					.andExpect(status().isForbidden());
		}
	}
}
