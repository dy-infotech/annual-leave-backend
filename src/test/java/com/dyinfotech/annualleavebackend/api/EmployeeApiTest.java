package com.dyinfotech.annualleavebackend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
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
 * 사원 본인 API(/api/employees) 통합 테스트.
 */
@DisplayName("사원 본인 API")
class EmployeeApiTest extends IntegrationTestSupport {

	@Autowired
	private PasswordEncoder passwordEncoder;

	private Employee 대표;
	private Employee 사원;

	@BeforeEach
	void setUpOrganization() {
		Department department = 부서("사원API부서");
		Team team = 팀("사원API팀", department);
		대표 = 사원("대표", "사장", department, team, null, Role.EMPLOYEE, 20f);
		팀관리자(team, 대표, team);
		사원 = 사원("일반사원", "과장", department, team, 대표, Role.EMPLOYEE, 15f);
		사원.completeSignUp(passwordEncoder.encode("Current1!"));
		em.flush();
	}

	/** null 값을 담을 수 있는 요청 본문. Map.of는 null을 허용하지 않는다. */
	private Map<String, Object> body(String key, Object value) {
		Map<String, Object> map = new HashMap<>();
		map.put(key, value);
		return map;
	}

	@Nested
	@DisplayName("내 정보 조회 GET /api/employees/me")
	class GetMyInfo {

		@Test
		void 로그인한_사원의_정보를_돌려준다() throws Exception {
			mockMvc.perform(get("/api/employees/me")
					.header("Authorization", bearer(사원)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.employeeNumber").value(사원.getEmployeeNumber()))
					.andExpect(jsonPath("$.name").value("일반사원"))
					.andExpect(jsonPath("$.position").value("과장"))
					.andExpect(jsonPath("$.department").value("사원API부서"))
					.andExpect(jsonPath("$.team").value("사원API팀"));
		}

		@Test
		void 결재자_정보가_함께_내려온다() throws Exception {
			mockMvc.perform(get("/api/employees/me")
					.header("Authorization", bearer(사원)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.approverName").value("대표"));
		}

		@Test
		void 토큰이_없으면_거부된다() throws Exception {
			mockMvc.perform(get("/api/employees/me"))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("이메일 변경 PATCH /api/employees/me/email")
	class ChangeEmail {

		@Test
		void 유효한_이메일이면_변경된다() throws Exception {
			mockMvc.perform(patch("/api/employees/me/email")
					.header("Authorization", bearer(사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("email", "new@example.com"))))
					.andExpect(status().isOk());

			em.flush();
			em.clear();
			assertThat(em.find(Employee.class, 사원.getEmployeeId()).getEmail())
					.isEqualTo("new@example.com");
		}

		@Test
		void 형식이_잘못된_이메일이면_400을_반환한다() throws Exception {
			mockMvc.perform(patch("/api/employees/me/email")
					.header("Authorization", bearer(사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("email", "not-an-email"))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 이메일이_비어_있으면_400을_반환한다() throws Exception {
			mockMvc.perform(patch("/api/employees/me/email")
					.header("Authorization", bearer(사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("email", ""))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 이메일이_null이면_400을_반환한다() throws Exception {
			mockMvc.perform(patch("/api/employees/me/email")
					.header("Authorization", bearer(사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(body("email", null))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 다른_사원과_같은_이메일도_허용된다() throws Exception {
			// 이메일 중복 검사가 없다. 현재 동작을 기록한다.
			mockMvc.perform(patch("/api/employees/me/email")
					.header("Authorization", bearer(사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("email", 대표.getEmail()))))
					.andExpect(status().isOk());
		}

		@Test
		void 토큰이_없으면_거부된다() throws Exception {
			mockMvc.perform(patch("/api/employees/me/email")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of("email", "new@example.com"))))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("비밀번호 변경 PATCH /api/employees/me/password")
	class ChangePassword {

		@Test
		void 현재_비밀번호가_맞으면_변경되고_204를_반환한다() throws Exception {
			mockMvc.perform(patch("/api/employees/me/password")
					.header("Authorization", bearer(사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"currentPassword", "Current1!",
							"newPassword", "Changed2@"))))
					.andExpect(status().isNoContent());

			em.flush();
			em.clear();
			String stored = em.find(Employee.class, 사원.getEmployeeId()).getPassword();
			assertThat(passwordEncoder.matches("Changed2@", stored)).isTrue();
			assertThat(passwordEncoder.matches("Current1!", stored)).isFalse();
		}

		@Test
		void 현재_비밀번호가_틀리면_401을_반환한다() throws Exception {
			mockMvc.perform(patch("/api/employees/me/password")
					.header("Authorization", bearer(사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"currentPassword", "WrongPass1!",
							"newPassword", "Changed2@"))))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void 현재_비밀번호가_비어_있으면_400을_반환한다() throws Exception {
			mockMvc.perform(patch("/api/employees/me/password")
					.header("Authorization", bearer(사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"currentPassword", "",
							"newPassword", "Changed2@"))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 새_비밀번호가_비어_있으면_400을_반환한다() throws Exception {
			mockMvc.perform(patch("/api/employees/me/password")
					.header("Authorization", bearer(사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"currentPassword", "Current1!",
							"newPassword", ""))))
					.andExpect(status().isBadRequest());
		}

		@Test
		void 새_비밀번호의_복잡도는_검증하지_않는다() throws Exception {
			// 서버에는 길이/문자 조합 규칙이 없다. 현재 동작을 기록한다.
			mockMvc.perform(patch("/api/employees/me/password")
					.header("Authorization", bearer(사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"currentPassword", "Current1!",
							"newPassword", "a"))))
					.andExpect(status().isNoContent());
		}

		@Test
		void 현재와_같은_비밀번호로도_변경할_수_있다() throws Exception {
			mockMvc.perform(patch("/api/employees/me/password")
					.header("Authorization", bearer(사원))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"currentPassword", "Current1!",
							"newPassword", "Current1!"))))
					.andExpect(status().isNoContent());
		}

		@Test
		void 토큰이_없으면_거부된다() throws Exception {
			mockMvc.perform(patch("/api/employees/me/password")
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(Map.of(
							"currentPassword", "Current1!",
							"newPassword", "Changed2@"))))
					.andExpect(status().isForbidden());
		}
	}
}
