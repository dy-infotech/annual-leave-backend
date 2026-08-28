package com.dyinfotech.annualleavebackend.support;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.common.security.jwt.JwtProvider;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

/**
 * API 통합 테스트 공통 기반.
 *
 * <p>테스트 DB에 연결하고 각 테스트를 트랜잭션 안에서 실행해 종료 시 롤백한다.
 * 외부로 나가는 메일/FCM은 목으로 대체해 실제 발송을 차단한다.
 *
 * <p>픽스처 헬퍼는 하나의 조직(부서 - 팀 - 사원)을 만들 수 있는 최소 단위만 제공한다.
 * 시드 데이터에 의존하지 않도록 각 테스트가 필요한 데이터를 직접 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestSupport {

	@Autowired
	protected MockMvc mockMvc;

	/** 요청 본문 직렬화 전용. 애플리케이션 빈에 의존하지 않는다. */
	protected final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	protected JwtProvider jwtProvider;

	@Autowired
	protected EntityManager em;

	/** 테스트 중 실제 메일이 나가지 않도록 차단한다. */
	@MockitoBean
	protected JavaMailSender mailSender;

	/** FCM은 외부 시스템이므로 호출 여부만 검증한다. */
	@MockitoBean
	protected NotificationService notificationService;

	/** 사번 자동 채번용 카운터. 테스트마다 겹치지 않는 사번을 만든다. */
	private int sequence;

	@BeforeEach
	void resetSequence() {
		sequence = 0;
	}

	protected String nextEmployeeNumber() {
		return String.format("T%07d", ++sequence);
	}

	// ------------------------------------------------------------------
	// 픽스처
	// ------------------------------------------------------------------

	protected Department 부서(String name) {
		Department department = Department.builder()
				.departmentName(name)
				.enabled(Boolean.TRUE)
				.build();
		em.persist(department);
		return department;
	}

	protected Team 팀(String name, Department department) {
		Team team = Team.builder()
				.teamName(name)
				.enabled(Boolean.TRUE)
				.department(department)
				.build();
		em.persist(team);
		return team;
	}

	/**
	 * 사원을 만든다. approver가 null이면 자기 자신을 결재자로 두는 최상위 사원으로 만든다.
	 *
	 * <p>approver_id가 NOT NULL이면서 자기 자신을 참조해야 하므로, 최상위 사원만
	 * 네이티브 INSERT로 만든 뒤 결재자를 자신으로 갱신한다. 시드 데이터도 같은 순서로
	 * 만든다(sql/data.sql 참고).
	 */
	protected Employee 사원(String name, String position, Department department, Team team,
			Employee approver, Role role, float totalLeaveDays) {
		if (approver == null) {
			return 최상위사원(name, position, department, team, role, totalLeaveDays);
		}
		Employee employee = Employee.builder()
				.employeeNumber(nextEmployeeNumber())
				.name(name)
				.department(department)
				.team(team)
				.position(position)
				.email("test@example.com")
				.role(role)
				.currYear(String.valueOf(LocalDate.now().getYear()))
				.currTotalLeaveDays(totalLeaveDays)
				.hireDate(LocalDate.now().minusYears(3))
				.approver(approver)
				.build();
		em.persist(employee);
		em.flush();
		return employee;
	}

	private Employee 최상위사원(String name, String position, Department department, Team team,
			Role role, float totalLeaveDays) {
		String employeeNumber = nextEmployeeNumber();
		em.flush();

		// 자기 자신을 참조해야 해서 먼저 임의의 결재자 값으로 넣고 곧바로 자신으로 교정한다.
		em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
		em.createNativeQuery("""
				INSERT INTO employee
					(employee_number, name, department_id, team_id, position, email,
					 hire_date, curr_year, curr_total_leave_days, prev_total_leave_days,
					 access_count, approver_id, created_at, created_ip, updated_at, updated_ip)
				VALUES
					(?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, NOW(), 'TEST', NOW(), 'TEST')
				""")
				.setParameter(1, employeeNumber)
				.setParameter(2, name)
				.setParameter(3, department.getDepartmentId())
				.setParameter(4, team.getTeamId())
				.setParameter(5, position)
				.setParameter(6, "test@example.com")
				.setParameter(7, LocalDate.now().minusYears(3))
				.setParameter(8, String.valueOf(LocalDate.now().getYear()))
				.setParameter(9, totalLeaveDays)
				.executeUpdate();
		em.createNativeQuery(
				"UPDATE employee SET approver_id = employee_id WHERE employee_number = ?")
				.setParameter(1, employeeNumber)
				.executeUpdate();
		em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();

		Long id = ((Number) em.createNativeQuery(
				"SELECT employee_id FROM employee WHERE employee_number = ?")
				.setParameter(1, employeeNumber)
				.getSingleResult()).longValue();
		em.clear();

		Employee employee = em.find(Employee.class, id);
		employee.changeRole(role);
		return employee;
	}

	protected TeamManager 팀관리자(Team team, Employee manager, Team parentTeam) {
		TeamManager teamManager = TeamManager.builder()
				.team(team)
				.projectManager(manager)
				.parentTeam(parentTeam)
				.build();
		em.persist(teamManager);
		em.flush();
		return teamManager;
	}

	// ------------------------------------------------------------------
	// 인증
	// ------------------------------------------------------------------

	/** 해당 사원으로 인증된 Authorization 헤더 값을 만든다. */
	protected String bearer(Employee employee, Role role) {
		return "Bearer " + jwtProvider.generateToken(employee.getEmployeeId(), role.name());
	}

	protected String bearer(Employee employee) {
		return bearer(employee, Role.EMPLOYEE);
	}

	protected String adminBearer(Employee employee) {
		return bearer(employee, Role.ADMIN);
	}

	protected String toJson(Object body) {
		try {
			return objectMapper.writeValueAsString(body);
		} catch (Exception e) {
			throw new IllegalStateException("테스트 요청 본문 직렬화 실패", e);
		}
	}
}
