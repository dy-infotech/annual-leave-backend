package com.dyinfotech.annualleavebackend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.support.IntegrationTestSupport;

/**
 * 인증 API(/api/auth) 통합 테스트.
 */
@DisplayName("인증 API")
class AuthApiTest extends IntegrationTestSupport {

	@Autowired
	private PasswordEncoder passwordEncoder;

	private Employee 미등록사원;
	private Employee 등록사원;

	@BeforeEach
	void setUpOrganization() {
		Department 부서 = 부서("테스트부서");
		Team 팀 = 팀("테스트팀", 부서);
		등록사원 = 사원("등록자", "사장", 부서, 팀, null, Role.EMPLOYEE, 15f);
		등록사원.completeSignUp(passwordEncoder.encode("Valid1234!"));
		// 로그인 성공 경로에서 결재선을 다시 계산하므로 팀 관리자가 있어야 한다.
		팀관리자(팀, 등록사원, 팀);
		미등록사원 = 사원("미등록자", "사원", 부서, 팀, 등록사원, Role.EMPLOYEE, 15f);
		em.flush();
	}

	@Nested
	@DisplayName("사용 등록 POST /api/auth/signup")
	class SignUp {

		@Test
		void 미등록_사번이면_등록에_성공한다() throws Exception {
			mockMvc.perform(post("/api/auth/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"employeeNumber", 미등록사원.getEmployeeNumber(),
							"password", "Valid1234!"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.name").value("미등록자"));

			em.flush();
			em.clear();
			assertThat(em.find(Employee.class, 미등록사원.getEmployeeId()).getPassword()).isNotBlank();
		}

		@Test
		void 이미_등록된_사번이면_409를_반환한다() throws Exception {
			mockMvc.perform(post("/api/auth/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"employeeNumber", 등록사원.getEmployeeNumber(),
							"password", "Valid1234!"))))
					.andExpect(status().isConflict());
		}

		@Test
		void 존재하지_않는_사번이면_404를_반환한다() throws Exception {
			mockMvc.perform(post("/api/auth/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"employeeNumber", "NOPE9999",
							"password", "Valid1234!"))))
					.andExpect(status().isNotFound());
		}

		@Test
		void 사번이_비어_있으면_400을_반환한다() throws Exception {
			mockMvc.perform(post("/api/auth/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"employeeNumber", "",
							"password", "Valid1234!"))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 비밀번호가_비어_있으면_400을_반환한다() throws Exception {
			mockMvc.perform(post("/api/auth/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"employeeNumber", 미등록사원.getEmployeeNumber(),
							"password", ""))))
					.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("로그인 POST /api/auth/signin")
	class SignIn {

		@Test
		void 사번과_비밀번호가_맞으면_토큰을_발급한다() throws Exception {
			mockMvc.perform(post("/api/auth/signin")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"employeeNumber", 등록사원.getEmployeeNumber(),
							"password", "Valid1234!"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.token").isNotEmpty())
					.andExpect(jsonPath("$.name").value("등록자"))
					.andExpect(jsonPath("$.role").isNotEmpty());
		}

		@Test
		void 비밀번호가_틀리면_401을_반환한다() throws Exception {
			mockMvc.perform(post("/api/auth/signin")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"employeeNumber", 등록사원.getEmployeeNumber(),
							"password", "WrongPass1!"))))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void 존재하지_않는_사번이면_401을_반환한다() throws Exception {
			mockMvc.perform(post("/api/auth/signin")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"employeeNumber", "NOPE9999",
							"password", "Valid1234!"))))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void 사용_등록이_안_된_사번이면_401을_반환한다() throws Exception {
			mockMvc.perform(post("/api/auth/signin")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"employeeNumber", 미등록사원.getEmployeeNumber(),
							"password", "Valid1234!"))))
					.andExpect(status().isUnauthorized());
		}
	}

	@Nested
	@DisplayName("이메일 찾기")
	class FindEmail {

		@Test
		void 사번으로_이메일을_찾는다() throws Exception {
			mockMvc.perform(post("/api/auth/find-email-by-employee-number")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("employeeNumber", 등록사원.getEmployeeNumber()))))
					.andExpect(status().isOk());
		}

		@Test
		void 없는_사번으로_찾아도_200을_반환한다() throws Exception {
			// 계정 존재 여부가 드러나지 않도록 404가 아닌 200으로 응답한다.
			// 현재 구현은 빈 목록이 아니라 null 원소 하나를 담아 반환한다.
			mockMvc.perform(post("/api/auth/find-email-by-employee-number")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("employeeNumber", "NOPE9999"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.maskedEmailList[0]").doesNotExist());
		}
	}

	@Nested
	@DisplayName("인증이 필요한 경로")
	class Protected {

		// 인증 실패 시 현재 구현은 401이 아니라 403으로 응답한다.
		// (AuthenticationEntryPoint 미설정 시의 Spring Security 기본 동작)
		@Test
		void 토큰_없이_보호된_API를_호출하면_거부된다() throws Exception {
			mockMvc.perform(post("/api/auth/logout")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of())))
					.andExpect(status().isForbidden());
		}

		@Test
		void 위조된_토큰이면_거부된다() throws Exception {
			mockMvc.perform(post("/api/auth/logout")
					.header("Authorization", "Bearer this.is.not.a.valid.token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of())))
					.andExpect(status().isForbidden());
		}

		@Test
		void 유효한_토큰이면_로그아웃에_성공한다() throws Exception {
			mockMvc.perform(post("/api/auth/logout")
					.header("Authorization", bearer(등록사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("fcmToken", "test-fcm-token"))))
					.andExpect(status().isOk());
		}
	}
}
