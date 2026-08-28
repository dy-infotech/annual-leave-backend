package com.dyinfotech.annualleavebackend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.dyinfotech.annualleavebackend.common.type.LeaveRequestStatus;
import com.dyinfotech.annualleavebackend.common.type.LeaveType;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.service.CommonService;
import com.dyinfotech.annualleavebackend.support.IntegrationTestSupport;

/**
 * 휴가 신청 생성 API(POST /api/leave-requests) 통합 테스트.
 *
 * <p>휴가 종류 7종의 신청 성공 경로와 사용일수/기간/잔여 연차/중복 검증을 모두 다룬다.
 *
 * <p>테스트 DB에는 시드 공휴일과 시드 사원이 이미 들어 있으므로, 날짜는 하드코딩하지 않고
 * 오늘을 기준으로 실제 공휴일 테이블을 읽어 계산한다.
 */
@DisplayName("휴가 신청 생성 API")
class LeaveRequestCreateApiTest extends IntegrationTestSupport {

	/** 팀/부서 이름은 유니크 제약이 있고 팀 관리자 캐시(24시간 TTL)의 키이므로 테스트마다 새 이름을 쓴다. */
	private static final AtomicInteger UNIQUE = new AtomicInteger();

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private CommonService commonService;

	private Employee 신청자;

	/** 오늘 이후의 근무일(주말/공휴일 제외) 목록. 첫 조회 시점의 공휴일 테이블 기준으로 만든다. */
	private List<LocalDate> 근무일목록;

	@BeforeEach
	void setUpOrganization() {
		캐시초기화();
		근무일목록 = null;

		String 식별자 = "-" + UNIQUE.incrementAndGet() + "-" + (System.nanoTime() % 1_000_000L);
		Department 부서 = 부서("휴가부서" + 식별자);
		Team 팀 = 팀("휴가팀" + 식별자, 부서);
		// 신청 성공 경로에서 결재선을 다시 계산하므로 팀 관리자가 반드시 있어야 한다.
		신청자 = 사원("신청자", "팀장", 부서, 팀, null, Role.EMPLOYEE, 15f);
		팀관리자(팀, 신청자, 팀);
		em.flush();
	}

	@AfterEach
	void tearDownCache() {
		// 롤백되는 공휴일 픽스처가 캐시에 남아 다음 테스트의 평일수 계산을 망가뜨리지 않도록 비운다.
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

	/** 오늘 이후 n번째 근무일. 주말과 공휴일 테이블에 등록된 날짜를 건너뛴다. */
	private LocalDate 근무일(int 순번) {
		if (근무일목록 == null) {
			LocalDate 오늘 = LocalDate.now();
			LocalDate 마지막 = 오늘.plusYears(1).minusDays(1);
			Set<LocalDate> 공휴일 = new HashSet<>(em.createQuery(
					"select h.holidayDate from Holiday h where h.holidayDate between :시작 and :종료", LocalDate.class)
					.setParameter("시작", 오늘)
					.setParameter("종료", 마지막)
					.getResultList());

			근무일목록 = new ArrayList<>();
			for (LocalDate 날짜 = 오늘.plusDays(1); !날짜.isAfter(마지막); 날짜 = 날짜.plusDays(1)) {
				DayOfWeek 요일 = 날짜.getDayOfWeek();
				if (요일 != DayOfWeek.SATURDAY && 요일 != DayOfWeek.SUNDAY && !공휴일.contains(날짜)) {
					근무일목록.add(날짜);
				}
			}
		}
		return 근무일목록.get(순번 - 1);
	}

	private LocalDate 다음토요일() {
		return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
	}

	private void 공휴일추가(LocalDate 날짜, String 이름) {
		em.createNativeQuery("DELETE FROM holiday WHERE holiday_date = ?")
				.setParameter(1, 날짜)
				.executeUpdate();
		em.createNativeQuery("INSERT INTO holiday (holiday_date, name) VALUES (?, ?)")
				.setParameter(1, 날짜)
				.setParameter(2, 이름)
				.executeUpdate();
	}

	/**
	 * 상태를 지정해 휴가 신청을 직접 만든다.
	 *
	 * <p>승인/반려 상태는 서비스 API로 만들 수 없고, 영속성 컨텍스트에 남은 엔티티가
	 * 조회 결과를 가리는 일도 없어야 해서 네이티브 INSERT로 넣는다.
	 */
	private Long 휴가신청_직접생성(Employee 사원, LeaveType 유형, LocalDate 시작일, LocalDate 종료일,
			float 사용일수, LeaveRequestStatus 상태) {
		em.flush();
		em.createNativeQuery("""
				INSERT INTO leave_request
					(employee_id, leave_type, start_date, end_date, use_days,
					 prev_total_leave_days, curr_total_leave_days, leave_reason,
					 status, created_at, created_ip)
				VALUES (?, ?, ?, ?, ?, 0, ?, '픽스처', ?, NOW(), 'TEST')
				""")
				.setParameter(1, 사원.getEmployeeId())
				.setParameter(2, 유형.getName())
				.setParameter(3, 시작일)
				.setParameter(4, 종료일)
				.setParameter(5, 사용일수)
				.setParameter(6, 사용일수)
				.setParameter(7, 상태.name())
				.executeUpdate();
		return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
	}

	private String 신청본문(String 휴가유형, LocalDate 시작일, LocalDate 종료일, Double 사용일수, String 사유) {
		Map<String, Object> 본문 = new HashMap<>();
		본문.put("leaveType", 휴가유형);
		본문.put("startDate", 시작일 == null ? null : 시작일.toString());
		본문.put("endDate", 종료일 == null ? null : 종료일.toString());
		본문.put("useDays", 사용일수);
		본문.put("leaveReason", 사유);
		return toJson(본문);
	}

	private ResultActions 휴가신청(String 휴가유형, LocalDate 시작일, LocalDate 종료일, Double 사용일수) throws Exception {
		return 휴가신청(휴가유형, 시작일, 종료일, 사용일수, "테스트 사유");
	}

	private ResultActions 휴가신청(String 휴가유형, LocalDate 시작일, LocalDate 종료일, Double 사용일수, String 사유)
			throws Exception {
		return mockMvc.perform(post("/api/leave-requests")
				.header("Authorization", bearer(신청자))
				.contentType(MediaType.APPLICATION_JSON)
				.content(신청본문(휴가유형, 시작일, 종료일, 사용일수, 사유)));
	}

	private long 내_신청_건수() {
		return em.createQuery(
				"select count(lr) from LeaveRequest lr where lr.employee.employeeId = :사원", Long.class)
				.setParameter("사원", 신청자.getEmployeeId())
				.getSingleResult();
	}

	// ------------------------------------------------------------------
	// 테스트
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("휴가 종류 7종 신청")
	class 휴가종류 {

		@Test
		void 연차_FULL_신청에_성공한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.FULL.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.requestId").isNumber())
					.andExpect(jsonPath("$.leaveType").value("FULL"))
					.andExpect(jsonPath("$.startDate").value(날짜.toString()))
					.andExpect(jsonPath("$.endDate").value(날짜.toString()))
					.andExpect(jsonPath("$.useDays").value(1.0))
					.andExpect(jsonPath("$.status").value(LeaveRequestStatus.PENDING.name()));

			assertThat(내_신청_건수()).isEqualTo(1);
		}

		@Test
		void 반차_오전_AM_HALF_신청에_성공한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.AM_HALF.getName(), 날짜, 날짜, 0.5)
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.leaveType").value("AM_HALF"))
					.andExpect(jsonPath("$.useDays").value(0.5));
		}

		@Test
		void 반차_오후_PM_HALF_신청에_성공한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.PM_HALF.getName(), 날짜, 날짜, 0.5)
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.leaveType").value("PM_HALF"))
					.andExpect(jsonPath("$.useDays").value(0.5));
		}

		@Test
		void 대체휴가_ALTERNATIVE_신청에_성공한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.ALTERNATIVE.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.leaveType").value("ALTERNATIVE"));
		}

		@Test
		void 출산휴가_PARENTAL_신청에_성공한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.PARENTAL.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.leaveType").value("PARENTAL"));
		}

		@Test
		void 가족돌봄휴가_FAMILY_신청에_성공한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.FAMILY.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.leaveType").value("FAMILY"));
		}

		@Test
		void 기타_OTHER_신청에_성공한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.OTHER.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.leaveType").value("OTHER"));
		}

		@Test
		void 정의되지_않은_휴가유형이면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청("SICK", 날짜, 날짜, 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("휴가유형 파라미터가 잘못되었습니다."));
		}

		@Test
		void 휴가유형_대소문자가_다르면_400을_반환한다() throws Exception {
			// LeaveType.fromName은 정확히 일치하는 이름만 인정한다.
			LocalDate 날짜 = 근무일(1);

			휴가신청("full", 날짜, 날짜, 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("휴가유형 파라미터가 잘못되었습니다."));
		}
	}

	@Nested
	@DisplayName("연차를 차감하지 않는 휴가(대체/출산/가족돌봄)")
	class 연차_비차감_휴가 {

		// LeaveRequestService.createLeaveRequest는 ALTERNATIVE/PARENTAL/FAMILY에 대해서만
		// validateUseDaysWithinWeekdays(평일수)와 validateRemainingLeave(잔여 연차)를 건너뛴다.

		@Test
		void 대체휴가는_평일이_없는_주말에도_신청할_수_있다() throws Exception {
			LocalDate 토요일 = 다음토요일();

			휴가신청(LeaveType.ALTERNATIVE.getName(), 토요일, 토요일, 1.0)
					.andExpect(status().isCreated());
		}

		@Test
		void 연차는_평일이_없는_주말이면_400을_반환한다() throws Exception {
			// 위 대체휴가 케이스와 같은 조건인데도 연차는 평일수 검증에 걸린다.
			LocalDate 토요일 = 다음토요일();

			휴가신청(LeaveType.FULL.getName(), 토요일, 토요일, 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("사용일수(1.0일)가 신청 기간 내 평일 수(0일)를 초과했습니다."));
		}

		@Test
		void 출산휴가는_잔여_연차를_초과해도_신청할_수_있다() throws Exception {
			LocalDate 날짜 = 근무일(1);
			float 잔여연차 = commonService.getRemainingDays(신청자, 0f);

			// 하루짜리 기간에 잔여 연차를 크게 넘는 일수를 넣어도 통과한다.
			휴가신청(LeaveType.PARENTAL.getName(), 날짜, 날짜, (double) (잔여연차 + 50f))
					.andExpect(status().isCreated());
		}

		@Test
		void 가족돌봄휴가는_평일수를_초과해도_신청할_수_있다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.FAMILY.getName(), 날짜, 날짜, 5.0)
					.andExpect(status().isCreated());
		}

		@Test
		void 기타휴가는_잔여_연차를_차감하므로_초과하면_400을_반환한다() throws Exception {
			// OTHER는 예외 목록에 없으므로 연차와 동일하게 검증된다.
			float 잔여연차 = commonService.getRemainingDays(신청자, 0f);
			int 필요근무일수 = (int) Math.ceil(잔여연차) + 1;

			휴가신청(LeaveType.OTHER.getName(), 근무일(1), 근무일(필요근무일수), (double) (잔여연차 + 1f))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("잔여 연차(" + 잔여연차 + "일)를 초과했습니다."));
		}

		@Test
		void 대체휴가는_잔여_연차_사용량에_반영되지_않는다() throws Exception {
			float 잔여연차 = commonService.getRemainingDays(신청자, 0f);

			// 대체휴가를 크게 신청해도
			휴가신청(LeaveType.ALTERNATIVE.getName(), 근무일(1), 근무일(1), (double) (잔여연차 + 10f))
					.andExpect(status().isCreated());

			// 연차 잔여일수는 그대로라 잔여 연차 전부를 다시 쓸 수 있다.
			휴가신청(LeaveType.FULL.getName(), 근무일(2), 근무일((int) 잔여연차 + 1), (double) 잔여연차)
					.andExpect(status().isCreated());
		}
	}

	@Nested
	@DisplayName("사용일수 단위 검증")
	class 사용일수_단위 {

		@Test
		void 연차는_1일_단위가_아니면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			// LeaveUnitType.DAY.getValidationMessage()는 마침표 없이 끝난다.
			휴가신청(LeaveType.FULL.getName(), 날짜, 날짜, 1.5)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("1일 단위로 사용 가능합니다"));
		}

		@Test
		void 기타휴가도_1일_단위만_허용한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.OTHER.getName(), 날짜, 날짜, 0.5)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("1일 단위로 사용 가능합니다"));
		}

		@Test
		void 반차는_0_5일_단위가_아니면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.AM_HALF.getName(), 날짜, 날짜, 0.7)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("0.5일 단위로 사용 가능합니다"));
		}

		@Test
		void 반차는_1일_단위도_0_5일의_배수라서_통과한다() throws Exception {
			// 명세상 반차는 0.5일이지만 구현은 0.5의 배수이면서 1일 이하이면 허용한다.
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.PM_HALF.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.useDays").value(1.0));
		}

		@Test
		void 반차가_1일을_초과하면_400을_반환한다() throws Exception {
			휴가신청(LeaveType.AM_HALF.getName(), 근무일(1), 근무일(2), 1.5)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("시간 단위 휴가는 1일을 초과할 수 없습니다."));
		}

		@Test
		void 사용일수가_0이면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.FULL.getName(), 날짜, 날짜, 0.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("사용일수는 0보다 커야 합니다."));
		}

		@Test
		void 사용일수가_음수면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.FULL.getName(), 날짜, 날짜, -1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("사용일수는 0보다 커야 합니다."));
		}
	}

	@Nested
	@DisplayName("필수 값 검증")
	class 필수값 {

		@Test
		void 휴가유형이_없으면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(null, 날짜, 날짜, 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("leaveType: 휴가유형을 입력해주세요."));
		}

		@Test
		void 시작일이_없으면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.FULL.getName(), null, 날짜, 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("startDate: 시작일을 입력해주세요."));
		}

		@Test
		void 종료일이_없으면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.FULL.getName(), 날짜, null, 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("endDate: 종료일을 입력해주세요."));
		}

		@Test
		void 사용일수가_없으면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.FULL.getName(), 날짜, 날짜, null)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("useDays: 사용일수를 입력해주세요."));
		}

		@Test
		void 날짜_형식이_잘못되면_400을_반환한다() throws Exception {
			Map<String, Object> 본문 = new HashMap<>();
			본문.put("leaveType", LeaveType.FULL.getName());
			본문.put("startDate", "2026/09/01");
			본문.put("endDate", "2026-09-01");
			본문.put("useDays", 1.0);

			mockMvc.perform(post("/api/leave-requests")
					.header("Authorization", bearer(신청자))
					.contentType(MediaType.APPLICATION_JSON)
					.content(toJson(본문)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));
		}

		@Test
		void 인증_없이_신청하면_거부된다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			// 인증 실패 시 현재 구현은 401이 아니라 403으로 응답한다.
			mockMvc.perform(post("/api/leave-requests")
					.contentType(MediaType.APPLICATION_JSON)
					.content(신청본문(LeaveType.FULL.getName(), 날짜, 날짜, 1.0, null)))
					.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("신청 기간 검증")
	class 신청기간 {

		@Test
		void 종료일이_시작일보다_빠르면_400을_반환한다() throws Exception {
			휴가신청(LeaveType.FULL.getName(), 근무일(3), 근무일(1), 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("종료일은 시작일 이후여야 합니다."));
		}

		@Test
		void 신청_가능_기간_이전이면_400을_반환한다() throws Exception {
			// 픽스처 입사일이 3년 전 오늘이라 신청 가능 기간은 오늘부터 1년간이다.
			LocalDate 어제 = LocalDate.now().minusDays(1);

			휴가신청(LeaveType.FULL.getName(), 어제, 어제, 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message")
							.value(org.hamcrest.Matchers.startsWith("휴가 신청 가능 기간을 벗어났습니다.")));
		}

		@Test
		void 신청_가능_기간_이후면_400을_반환한다() throws Exception {
			LocalDate 일년뒤 = LocalDate.now().plusYears(1);

			휴가신청(LeaveType.FULL.getName(), 일년뒤, 일년뒤, 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message")
							.value(org.hamcrest.Matchers.startsWith("휴가 신청 가능 기간을 벗어났습니다.")));
		}

		@Test
		void 신청_가능_기간의_마지막날은_허용된다() throws Exception {
			LocalDate 마지막날 = LocalDate.now().plusYears(1).minusDays(1);

			// 마지막날이 주말/공휴일일 수 있으므로 연차 차감이 없는 대체휴가로 경계만 확인한다.
			휴가신청(LeaveType.ALTERNATIVE.getName(), 마지막날, 마지막날, 1.0)
					.andExpect(status().isCreated());
		}
	}

	@Nested
	@DisplayName("평일수 검증 (주말/공휴일 제외)")
	class 평일수 {

		@Test
		void 공휴일은_평일수에서_제외되어_사용일수가_초과되면_400을_반환한다() throws Exception {
			LocalDate 시작일 = 근무일(1);
			LocalDate 종료일 = 근무일(5);
			공휴일추가(근무일(3), "테스트 임시 공휴일");

			// 근무일 5일 중 하루가 공휴일이 되어 평일은 4일로 줄어든다.
			휴가신청(LeaveType.FULL.getName(), 시작일, 종료일, 5.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("사용일수(5.0일)가 신청 기간 내 평일 수(4일)를 초과했습니다."));
		}

		@Test
		void 공휴일을_제외한_평일수_이내면_신청에_성공한다() throws Exception {
			LocalDate 시작일 = 근무일(1);
			LocalDate 종료일 = 근무일(5);
			공휴일추가(근무일(3), "테스트 임시 공휴일");

			휴가신청(LeaveType.FULL.getName(), 시작일, 종료일, 4.0)
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.useDays").value(4.0));
		}

		@Test
		void 공휴일이_없으면_근무일수만큼_신청할_수_있다() throws Exception {
			휴가신청(LeaveType.FULL.getName(), 근무일(1), 근무일(5), 5.0)
					.andExpect(status().isCreated());
		}

		@Test
		void 주말만_포함된_기간이면_400을_반환한다() throws Exception {
			LocalDate 토요일 = 다음토요일();

			휴가신청(LeaveType.FULL.getName(), 토요일, 토요일.plusDays(1), 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("사용일수(1.0일)가 신청 기간 내 평일 수(0일)를 초과했습니다."));
		}
	}

	@Nested
	@DisplayName("잔여 연차 검증")
	class 잔여연차 {

		@Test
		void 잔여_연차를_초과하면_400을_반환한다() throws Exception {
			float 잔여 = commonService.getRemainingDays(신청자, 0f);
			int 필요근무일수 = (int) Math.ceil(잔여) + 1;

			휴가신청(LeaveType.FULL.getName(), 근무일(1), 근무일(필요근무일수), (double) (잔여 + 1f))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("잔여 연차(" + 잔여 + "일)를 초과했습니다."));
		}

		@Test
		void 잔여_연차와_같으면_신청에_성공한다() throws Exception {
			float 잔여 = commonService.getRemainingDays(신청자, 0f);

			휴가신청(LeaveType.FULL.getName(), 근무일(1), 근무일((int) 잔여), (double) 잔여)
					.andExpect(status().isCreated());
		}

		@Test
		void 이미_신청한_연차만큼_잔여가_줄어든다() throws Exception {
			float 잔여 = commonService.getRemainingDays(신청자, 0f);

			휴가신청(LeaveType.FULL.getName(), 근무일(1), 근무일(1), 1.0)
					.andExpect(status().isCreated());

			// 대기(PENDING) 상태도 사용량에 포함되므로 남은 일수는 1일 줄어든다.
			휴가신청(LeaveType.FULL.getName(), 근무일(2), 근무일((int) 잔여 + 1), (double) 잔여)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("잔여 연차(" + (잔여 - 1f) + "일)를 초과했습니다."));
		}
	}

	@Nested
	@DisplayName("기간 중복 검증")
	class 기간중복 {

		@Test
		void 같은_기간을_다시_신청하면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.FULL.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isCreated());

			휴가신청(LeaveType.FULL.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("이미 신청된 연차 기간과 중복됩니다."));
		}

		@Test
		void 기간이_일부만_겹쳐도_400을_반환한다() throws Exception {
			휴가신청(LeaveType.FULL.getName(), 근무일(1), 근무일(3), 3.0)
					.andExpect(status().isCreated());

			휴가신청(LeaveType.FULL.getName(), 근무일(3), 근무일(5), 3.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("이미 신청된 연차 기간과 중복됩니다."));
		}

		@Test
		void 기간이_겹치지_않으면_신청에_성공한다() throws Exception {
			휴가신청(LeaveType.FULL.getName(), 근무일(1), 근무일(2), 2.0)
					.andExpect(status().isCreated());

			휴가신청(LeaveType.FULL.getName(), 근무일(3), 근무일(4), 2.0)
					.andExpect(status().isCreated());
		}

		@Test
		void 같은_날_오전반차와_오후반차는_함께_신청할_수_있다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.AM_HALF.getName(), 날짜, 날짜, 0.5)
					.andExpect(status().isCreated());

			휴가신청(LeaveType.PM_HALF.getName(), 날짜, 날짜, 0.5)
					.andExpect(status().isCreated());

			assertThat(내_신청_건수()).isEqualTo(2);
		}

		@Test
		void 같은_날_오전반차를_두_번_신청하면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.AM_HALF.getName(), 날짜, 날짜, 0.5)
					.andExpect(status().isCreated());

			휴가신청(LeaveType.AM_HALF.getName(), 날짜, 날짜, 0.5)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("이미 신청된 연차 기간과 중복됩니다."));
		}

		@Test
		void 같은_날_오후반차를_두_번_신청하면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.PM_HALF.getName(), 날짜, 날짜, 0.5)
					.andExpect(status().isCreated());

			휴가신청(LeaveType.PM_HALF.getName(), 날짜, 날짜, 0.5)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("이미 신청된 연차 기간과 중복됩니다."));
		}

		@Test
		void 반차와_연차가_같은_날이면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			휴가신청(LeaveType.AM_HALF.getName(), 날짜, 날짜, 0.5)
					.andExpect(status().isCreated());

			// 반차 조합 예외는 양쪽 모두 반차일 때만 적용된다.
			휴가신청(LeaveType.FULL.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("이미 신청된 연차 기간과 중복됩니다."));
		}

		@Test
		void 취소된_신청과_겹치면_다시_신청할_수_있다() throws Exception {
			LocalDate 날짜 = 근무일(1);

			String 응답 = 휴가신청(LeaveType.FULL.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isCreated())
					.andReturn().getResponse().getContentAsString();
			long 신청번호 = objectMapper.readTree(응답).get("requestId").asLong();

			mockMvc.perform(delete("/api/leave-requests/{requestId}", 신청번호)
					.header("Authorization", bearer(신청자)))
					.andExpect(status().isNoContent());

			휴가신청(LeaveType.FULL.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isCreated());
		}

		@Test
		void 반려된_신청과_겹치면_다시_신청할_수_있다() throws Exception {
			LocalDate 날짜 = 근무일(1);
			휴가신청_직접생성(신청자, LeaveType.FULL, 날짜, 날짜, 1.0f, LeaveRequestStatus.REJECTED);

			휴가신청(LeaveType.FULL.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isCreated());
		}

		@Test
		void 승인된_신청과_겹치면_400을_반환한다() throws Exception {
			LocalDate 날짜 = 근무일(1);
			휴가신청_직접생성(신청자, LeaveType.FULL, 날짜, 날짜, 1.0f, LeaveRequestStatus.APPROVED);

			휴가신청(LeaveType.FULL.getName(), 날짜, 날짜, 1.0)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("이미 신청된 연차 기간과 중복됩니다."));
		}
	}
}
