package com.dyinfotech.annualleavebackend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.type.LeaveType;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.LeaveRequest;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.support.IntegrationTestSupport;

/**
 * 휴가 신청 조회/취소/공휴일 API 통합 테스트.
 *
 * <p>대상 엔드포인트
 * <ul>
 *   <li>GET /api/leave-requests/all</li>
 *   <li>GET /api/leave-requests/my</li>
 *   <li>GET /api/leave-requests/{requestId}</li>
 *   <li>DELETE /api/leave-requests/{requestId}</li>
 *   <li>GET /api/leave-requests/current-year-special-days</li>
 *   <li>GET /api/leave-requests/next-year-special-days</li>
 * </ul>
 *
 * <p>승인/반려/취소 상태는 서비스 API로 만들 수 없으므로 네이티브 INSERT로 픽스처를 만든다.
 */
@DisplayName("휴가 신청 조회 API")
class LeaveRequestQueryApiTest extends IntegrationTestSupport {

	/** 팀/부서 이름은 유니크 제약이 있고 팀 관리자 캐시(24시간 TTL)의 키이므로 테스트마다 새 이름을 쓴다. */
	private static final AtomicInteger UNIQUE = new AtomicInteger();

	@Autowired
	private CacheManager cacheManager;

	private Employee 신청자;
	private Employee 동료;

	/** 시드 데이터와 겹치지 않도록 충분히 떨어진 미래 날짜를 쓴다. */
	private LocalDate 기준일;

	@BeforeEach
	void setUpOrganization() {
		캐시초기화();
		기준일 = LocalDate.now().plusDays(40);

		String 식별자 = "-" + UNIQUE.incrementAndGet() + "-" + (System.nanoTime() % 1_000_000L);
		Department 부서 = 부서("조회부서" + 식별자);
		Team 팀 = 팀("조회팀" + 식별자, 부서);
		신청자 = 사원("신청자", "팀장", 부서, 팀, null, Role.EMPLOYEE, 15f);
		팀관리자(팀, 신청자, 팀);
		동료 = 사원("동료", "사원", 부서, 팀, 신청자, Role.EMPLOYEE, 15f);
		em.flush();
	}

	@AfterEach
	void tearDownCache() {
		캐시초기화();
	}

	// ------------------------------------------------------------------
	// 픽스처 / 헬퍼
	// ------------------------------------------------------------------

	private void 캐시초기화() {
		비우기(cacheManager.getCache(CacheConfig.CACHE_HOLIDAYS));
		비우기(cacheManager.getCache(CacheConfig.CACHE_EMPLOYEES));
	}

	private void 비우기(Cache cache) {
		if (cache != null) {
			cache.clear();
		}
	}

	/** 상태를 지정해 휴가 신청을 직접 만든다. 영속성 컨텍스트를 거치지 않아 조회 결과가 가려지지 않는다. */
	private Long 휴가신청(Employee 사원, LeaveType 유형, LocalDate 시작일, LocalDate 종료일,
			float 사용일수, String 사유, LeaveRequestStatus 상태) {
		em.flush();
		em.createNativeQuery("""
				INSERT INTO leave_request
					(employee_id, leave_type, start_date, end_date, use_days,
					 prev_total_leave_days, curr_total_leave_days, leave_reason,
					 status, created_at, created_ip)
				VALUES (?, ?, ?, ?, ?, 15, ?, ?, ?, NOW(), 'TEST')
				""")
				.setParameter(1, 사원.getEmployeeId())
				.setParameter(2, 유형.getName())
				.setParameter(3, 시작일)
				.setParameter(4, 종료일)
				.setParameter(5, 사용일수)
				.setParameter(6, 15f + 사용일수)
				.setParameter(7, 사유)
				.setParameter(8, 상태.name())
				.executeUpdate();
		return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
	}

	private Long 대기신청(Employee 사원, LocalDate 시작일, LocalDate 종료일) {
		return 휴가신청(사원, LeaveType.FULL, 시작일, 종료일, 1.0f, "개인 사유", LeaveRequestStatus.PENDING);
	}

	private void 공휴일교체(LocalDate 날짜, String 이름) {
		em.createNativeQuery("DELETE FROM holiday WHERE holiday_date = ?")
				.setParameter(1, 날짜)
				.executeUpdate();
		em.createNativeQuery("INSERT INTO holiday (holiday_date, name) VALUES (?, ?)")
				.setParameter(1, 날짜)
				.setParameter(2, 이름)
				.executeUpdate();
	}

	private long 공휴일수(int 연도) {
		return em.createQuery(
				"select count(h) from Holiday h where h.holidayDate between :시작 and :종료", Long.class)
				.setParameter("시작", Year.of(연도).atDay(1))
				.setParameter("종료", Year.of(연도).atMonth(Month.DECEMBER).atEndOfMonth())
				.getSingleResult();
	}

	private LeaveRequestStatus 상태(Long 신청번호) {
		return em.createQuery(
				"select lr.status from LeaveRequest lr where lr.requestId = :번호", LeaveRequestStatus.class)
				.setParameter("번호", 신청번호)
				.getSingleResult();
	}

	// ------------------------------------------------------------------
	// 테스트
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("전체 조회 GET /api/leave-requests/all")
	class 전체조회 {

		@Test
		void 조건_없이_조회하면_전체_신청을_반환한다() throws Exception {
			Long 내신청 = 대기신청(신청자, 기준일, 기준일);
			Long 동료신청 = 대기신청(동료, 기준일, 기준일);

			mockMvc.perform(get("/api/leave-requests/all")
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$..requestId", hasItem(내신청.intValue())))
					.andExpect(jsonPath("$..requestId", hasItem(동료신청.intValue())));
		}

		@Test
		void 사원_조건으로_필터링한다() throws Exception {
			Long 내신청 = 대기신청(신청자, 기준일, 기준일);
			Long 동료신청 = 대기신청(동료, 기준일, 기준일);

			mockMvc.perform(get("/api/leave-requests/all")
					.param("employeeId", String.valueOf(동료.getEmployeeId()))
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(1)))
					.andExpect(jsonPath("$..requestId", hasItem(동료신청.intValue())))
					.andExpect(jsonPath("$..requestId", not(hasItem(내신청.intValue()))))
					.andExpect(jsonPath("$[0].employeeName").value("동료"));
		}

		@Test
		void 상태_조건으로_필터링한다() throws Exception {
			Long 대기 = 대기신청(신청자, 기준일, 기준일);
			Long 반려 = 휴가신청(신청자, LeaveType.FULL, 기준일.plusDays(10), 기준일.plusDays(10),
					1.0f, "개인 사유", LeaveRequestStatus.REJECTED);

			mockMvc.perform(get("/api/leave-requests/all")
					.param("employeeId", String.valueOf(신청자.getEmployeeId()))
					.param("status", LeaveRequestStatus.PENDING.name())
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(1)))
					.andExpect(jsonPath("$..requestId", hasItem(대기.intValue())))
					.andExpect(jsonPath("$..requestId", not(hasItem(반려.intValue()))))
					.andExpect(jsonPath("$[0].status").value(LeaveRequestStatus.PENDING.name()));
		}

		@Test
		void 기간_조건으로_필터링한다() throws Exception {
			Long 기간내 = 대기신청(신청자, 기준일, 기준일);
			Long 기간밖 = 대기신청(신청자, 기준일.plusDays(30), 기준일.plusDays(30));

			// 검색 기간과 신청 기간이 겹치는 건만 반환한다.
			mockMvc.perform(get("/api/leave-requests/all")
					.param("employeeId", String.valueOf(신청자.getEmployeeId()))
					.param("startDate", 기준일.minusDays(1).toString())
					.param("endDate", 기준일.plusDays(1).toString())
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(1)))
					.andExpect(jsonPath("$..requestId", hasItem(기간내.intValue())))
					.andExpect(jsonPath("$..requestId", not(hasItem(기간밖.intValue()))));
		}

		@Test
		void 조회_시작일이_창립기념일_이전이면_400을_반환한다() throws Exception {
			mockMvc.perform(get("/api/leave-requests/all")
					.param("startDate", "2011-07-07")
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value(
							"회사 창립 기념일보다 이전 날짜를 요청할 수 없습니다. 요청한 시작일: 2011-07-07, 창립기념일: 2011-07-08"));
		}

		@Test
		void 조회_시작일이_창립기념일_당일이면_허용된다() throws Exception {
			mockMvc.perform(get("/api/leave-requests/all")
					.param("startDate", "2011-07-08")
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk());
		}

		@Test
		void 조회_종료일이_내년_12월_31일을_넘으면_400을_반환한다() throws Exception {
			LocalDate 초과일 = Year.now().plusYears(1).atMonth(Month.DECEMBER).atEndOfMonth().plusDays(1);

			mockMvc.perform(get("/api/leave-requests/all")
					.param("endDate", 초과일.toString())
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message")
							.value("내년 이후의 날짜를 지정할 수 없습니다. 요청한 종료일: " + 초과일));
		}

		@Test
		void 조회_종료일이_내년_12월_31일이면_허용된다() throws Exception {
			LocalDate 경계일 = Year.now().plusYears(1).atMonth(Month.DECEMBER).atEndOfMonth();

			mockMvc.perform(get("/api/leave-requests/all")
					.param("endDate", 경계일.toString())
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk());
		}

		@Test
		void 인증_없이_호출하면_거부된다() throws Exception {
			mockMvc.perform(get("/api/leave-requests/all"))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("내 신청 조회 GET /api/leave-requests/my")
	class 내신청조회 {

		@Test
		void 내_신청만_반환한다() throws Exception {
			Long 내신청 = 대기신청(신청자, 기준일, 기준일);
			Long 동료신청 = 대기신청(동료, 기준일, 기준일);

			mockMvc.perform(get("/api/leave-requests/my")
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$..requestId", hasItem(내신청.intValue())))
					.andExpect(jsonPath("$..requestId", not(hasItem(동료신청.intValue()))))
					.andExpect(jsonPath("$..employeeName", everyItem(is("신청자"))));
		}

		@Test
		void 요청에_담긴_사원_조건은_무시되고_내_신청만_반환한다() throws Exception {
			Long 동료신청 = 대기신청(동료, 기준일, 기준일);

			// 컨트롤러가 조회 조건의 employeeId를 인증된 사원으로 덮어쓴다.
			mockMvc.perform(get("/api/leave-requests/my")
					.param("employeeId", String.valueOf(동료.getEmployeeId()))
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$..requestId", not(hasItem(동료신청.intValue()))));
		}

		@Test
		void 신청_내역이_없으면_빈_배열을_반환한다() throws Exception {
			mockMvc.perform(get("/api/leave-requests/my")
					.header("Authorization", bearer(동료)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(0)));
		}

		@Test
		void 인증_없이_호출하면_거부된다() throws Exception {
			mockMvc.perform(get("/api/leave-requests/my"))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("상세 조회 GET /api/leave-requests/{requestId}")
	class 상세조회 {

		@Test
		void 본인이_조회하면_사유가_보인다() throws Exception {
			Long 신청번호 = 휴가신청(신청자, LeaveType.AM_HALF, 기준일, 기준일, 0.5f, "병원 진료", LeaveRequestStatus.PENDING);

			mockMvc.perform(get("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.employeeName").value("신청자"))
					.andExpect(jsonPath("$.leaveType").value("AM_HALF"))
					.andExpect(jsonPath("$.startDate").value(기준일.toString()))
					.andExpect(jsonPath("$.useDays").value(0.5))
					.andExpect(jsonPath("$.status").value(LeaveRequestStatus.PENDING.name()))
					.andExpect(jsonPath("$.leaveReason").value("병원 진료"))
					.andExpect(jsonPath("$.approverName").doesNotExist());
		}

		@Test
		void 관리자가_조회하면_타인의_사유도_보인다() throws Exception {
			Long 신청번호 = 휴가신청(동료, LeaveType.FULL, 기준일, 기준일, 1.0f, "가족 행사", LeaveRequestStatus.PENDING);

			mockMvc.perform(get("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", adminBearer(신청자)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.employeeName").value("동료"))
					.andExpect(jsonPath("$.leaveReason").value("가족 행사"));
		}

		@Test
		void 타인이_조회하면_사유가_null이다() throws Exception {
			Long 신청번호 = 휴가신청(동료, LeaveType.FULL, 기준일, 기준일, 1.0f, "가족 행사", LeaveRequestStatus.PENDING);

			mockMvc.perform(get("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.employeeName").value("동료"))
					.andExpect(jsonPath("$.leaveReason").doesNotExist());
		}

		@Test
		void 존재하지_않는_신청이면_500을_반환한다() throws Exception {
			// 서비스가 IllegalArgumentException을 던져 GlobalExceptionHandler의 500 처리로 떨어진다.
			// (취소 API가 404를 주는 것과 다르다.)
			mockMvc.perform(get("/api/leave-requests/{requestId}", 999_999_999L)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isInternalServerError())
					.andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
		}

		@Test
		void 인증_없이_호출하면_거부된다() throws Exception {
			Long 신청번호 = 대기신청(신청자, 기준일, 기준일);

			mockMvc.perform(get("/api/leave-requests/{requestId}", 신청번호))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("취소 DELETE /api/leave-requests/{requestId}")
	class 취소 {

		@Test
		void 본인의_대기_신청을_취소하면_204를_반환한다() throws Exception {
			Long 신청번호 = 대기신청(신청자, 기준일, 기준일);

			mockMvc.perform(delete("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isNoContent());

			em.flush();
			assertThat(상태(신청번호)).isEqualTo(LeaveRequestStatus.CANCELLED);
		}

		@Test
		void 시작일이_지난_대기_신청도_취소할_수_있다() throws Exception {
			// 대기 상태는 기간을 검사하지 않는다(LeaveRequest.cancel 참고).
			LocalDate 지난날 = LocalDate.now().minusDays(5);
			Long 신청번호 = 대기신청(신청자, 지난날, 지난날);

			mockMvc.perform(delete("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isNoContent());
		}

		@Test
		void 타인의_신청을_취소하면_403을_반환한다() throws Exception {
			Long 신청번호 = 대기신청(동료, 기준일, 기준일);

			mockMvc.perform(delete("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.message").value("본인의 휴가 신청만 취소할 수 있습니다."));

			em.flush();
			assertThat(상태(신청번호)).isEqualTo(LeaveRequestStatus.PENDING);
		}

		@Test
		void 관리자여도_타인의_신청은_취소할_수_없다() throws Exception {
			Long 신청번호 = 대기신청(동료, 기준일, 기준일);

			mockMvc.perform(delete("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", adminBearer(신청자)))
					.andExpect(status().isForbidden());
		}

		@Test
		void 이미_취소된_신청이면_409를_반환한다() throws Exception {
			Long 신청번호 = 휴가신청(신청자, LeaveType.FULL, 기준일, 기준일, 1.0f, "개인 사유",
					LeaveRequestStatus.CANCELLED);

			mockMvc.perform(delete("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.message").value("대기 또는 승인 상태인 신청만 취소할 수 있습니다."));
		}

		@Test
		void 반려된_신청이면_409를_반환한다() throws Exception {
			Long 신청번호 = 휴가신청(신청자, LeaveType.FULL, 기준일, 기준일, 1.0f, "개인 사유",
					LeaveRequestStatus.REJECTED);

			mockMvc.perform(delete("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.message").value("대기 또는 승인 상태인 신청만 취소할 수 있습니다."));
		}

		@Test
		void 승인된_신청의_시작일이_미래면_취소할_수_있다() throws Exception {
			Long 신청번호 = 휴가신청(신청자, LeaveType.FULL, 기준일, 기준일, 1.0f, "개인 사유",
					LeaveRequestStatus.APPROVED);

			mockMvc.perform(delete("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isNoContent());

			em.flush();
			assertThat(상태(신청번호)).isEqualTo(LeaveRequestStatus.CANCELLED);
		}

		@Test
		void 승인된_신청의_시작일이_오늘이면_409를_반환한다() throws Exception {
			LocalDate 오늘 = LocalDate.now();
			Long 신청번호 = 휴가신청(신청자, LeaveType.FULL, 오늘, 오늘, 1.0f, "개인 사유",
					LeaveRequestStatus.APPROVED);

			mockMvc.perform(delete("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.message").value("이미 시작되었거나 지난 휴가는 취소할 수 없습니다."));
		}

		@Test
		void 승인된_신청의_시작일이_과거면_409를_반환한다() throws Exception {
			LocalDate 지난날 = LocalDate.now().minusDays(3);
			Long 신청번호 = 휴가신청(신청자, LeaveType.FULL, 지난날, 지난날, 1.0f, "개인 사유",
					LeaveRequestStatus.APPROVED);

			mockMvc.perform(delete("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.message").value("이미 시작되었거나 지난 휴가는 취소할 수 없습니다."));
		}

		@Test
		void 존재하지_않는_신청이면_404를_반환한다() throws Exception {
			mockMvc.perform(delete("/api/leave-requests/{requestId}", 999_999_999L)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.message").value("존재하지 않는 휴가 신청 정보입니다."));
		}

		@Test
		void 인증_없이_호출하면_거부된다() throws Exception {
			Long 신청번호 = 대기신청(신청자, 기준일, 기준일);

			mockMvc.perform(delete("/api/leave-requests/{requestId}", 신청번호))
					.andExpect(status().isForbidden());

			em.flush();
			assertThat(상태(신청번호)).isEqualTo(LeaveRequestStatus.PENDING);
		}
	}

	@Nested
	@DisplayName("공휴일 조회")
	class 공휴일조회 {

		@Test
		void 금년_공휴일을_조회한다() throws Exception {
			int 금년 = LocalDate.now().getYear();
			LocalDate 임시공휴일 = LocalDate.of(금년, 6, 13);
			공휴일교체(임시공휴일, "테스트 금년 공휴일");

			mockMvc.perform(get("/api/leave-requests/current-year-special-days")
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize((int) 공휴일수(금년))))
					.andExpect(jsonPath("$..name", hasItem("테스트 금년 공휴일")))
					.andExpect(jsonPath("$..date", hasItem(임시공휴일.toString())));
		}

		@Test
		void 차년도_공휴일을_조회한다() throws Exception {
			int 차년도 = LocalDate.now().getYear() + 1;
			LocalDate 임시공휴일 = LocalDate.of(차년도, 6, 13);
			공휴일교체(임시공휴일, "테스트 차년도 공휴일");

			mockMvc.perform(get("/api/leave-requests/next-year-special-days")
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize((int) 공휴일수(차년도))))
					.andExpect(jsonPath("$..name", hasItem("테스트 차년도 공휴일")))
					.andExpect(jsonPath("$..date", hasItem(임시공휴일.toString())));
		}

		@Test
		void 금년_공휴일_조회는_차년도_공휴일을_포함하지_않는다() throws Exception {
			int 차년도 = LocalDate.now().getYear() + 1;
			LocalDate 임시공휴일 = LocalDate.of(차년도, 6, 13);
			공휴일교체(임시공휴일, "테스트 차년도 공휴일");

			mockMvc.perform(get("/api/leave-requests/current-year-special-days")
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$..name", not(hasItem("테스트 차년도 공휴일"))));
		}

		@Test
		void 인증_없이_금년_공휴일을_조회하면_거부된다() throws Exception {
			mockMvc.perform(get("/api/leave-requests/current-year-special-days"))
					.andExpect(status().isForbidden());
		}

		@Test
		void 인증_없이_차년도_공휴일을_조회하면_거부된다() throws Exception {
			mockMvc.perform(get("/api/leave-requests/next-year-special-days"))
					.andExpect(status().isForbidden());
		}
	}

	/** 픽스처가 실제로 저장되는지 확인해 두면 조회 테스트 실패 원인을 좁히기 쉽다. */
	@Test
	void 픽스처_휴가신청이_저장된다() {
		Long 신청번호 = 대기신청(신청자, 기준일, 기준일);

		LeaveRequest 저장된신청 = em.find(LeaveRequest.class, 신청번호);
		assertThat(저장된신청).isNotNull();
		assertThat(저장된신청.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
		assertThat(저장된신청.getEmployee().getEmployeeId()).isEqualTo(신청자.getEmployeeId());
	}
}
