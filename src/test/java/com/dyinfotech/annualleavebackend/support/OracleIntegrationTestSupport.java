package com.dyinfotech.annualleavebackend.support;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.dyinfotech.annualleavebackend.common.security.jwt.JwtProvider;
import com.dyinfotech.annualleavebackend.common.type.Role;
import com.dyinfotech.annualleavebackend.config.CacheConfig.OrganizationCacheKey;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamManagerCacheRow;
import com.dyinfotech.annualleavebackend.service.NotificationService;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.firebase.messaging.FirebaseMessaging;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_ORACLE_INTEGRATION_TESTS", matches = "true")
public abstract class OracleIntegrationTestSupport {

    protected static final String DEFAULT_PASSWORD = "Valid1234!";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected EntityManager em;

    @Autowired
    protected JwtProvider jwtProvider;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected CacheManager cacheManager;

    @Autowired
    @Qualifier("departmentLoadingCache")
    protected LoadingCache<OrganizationCacheKey, List<DepartmentCacheRow>> departmentCache;

    @Autowired
    @Qualifier("teamLoadingCache")
    protected LoadingCache<OrganizationCacheKey, List<TeamCacheRow>> teamCache;

    @Autowired
    @Qualifier("teamManagerLoadingCache")
    protected LoadingCache<String, List<TeamManagerCacheRow>> teamManagerCache;

    @MockitoBean
    protected JavaMailSender mailSender;

    @MockitoBean
    protected FirebaseMessaging firebaseMessaging;

    @MockitoBean
    protected NotificationService notificationService;

    @BeforeEach
    void clearSharedCaches() {
        departmentCache.invalidateAll();
        teamCache.invalidateAll();
        teamManagerCache.invalidateAll();
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    protected String unique(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet();
    }

    protected Employee seededCeo() {
        Employee ceo = em.createQuery(
                        "select e from Employee e where e.employeeNumber = :number",
                        Employee.class)
                .setParameter("number", "A2011001")
                .getSingleResult();
        if (!ceo.isRegisted()) {
            ceo.completeSignUp(passwordEncoder.encode(DEFAULT_PASSWORD));
            em.flush();
        }
        return ceo;
    }

    protected Department department(String name) {
        Department department = Department.builder()
                .departmentName(name)
                .enabled(Boolean.TRUE)
                .build();
        em.persist(department);
        em.flush();
        return department;
    }

    protected Team team(String name, Department department) {
        Team team = Team.builder()
                .teamName(name)
                .department(department)
                .enabled(Boolean.TRUE)
                .build();
        em.persist(team);
        em.flush();
        return team;
    }

    protected Employee employee(
            String name,
            String position,
            Department department,
            Team team,
            Employee approver,
            boolean registered) {
        Employee employee = Employee.builder()
                .employeeNumber(unique("IT"))
                .name(name)
                .department(department)
                .team(team)
                .position(position)
                .email(unique("user") + "@example.com")
                .role(Role.EMPLOYEE)
                .currYear(String.valueOf(LocalDate.now().getYear()))
                .currTotalLeaveDays(15.0f)
                .hireDate(LocalDate.now().minusYears(3))
                .approver(approver != null ? approver : seededCeo())
                .build();
        if (registered) {
            employee.completeSignUp(passwordEncoder.encode(DEFAULT_PASSWORD));
        }
        em.persist(employee);
        em.flush();
        return employee;
    }

    protected TeamManager teamManager(Team team, Employee manager, Team parentTeam) {
        TeamManager teamManager = TeamManager.builder()
                .team(team)
                .projectManager(manager)
                .parentTeam(parentTeam)
                .build();
        em.persist(teamManager);
        em.flush();
        teamManagerCache.invalidateAll();
        return teamManager;
    }

    protected String bearer(Employee employee) {
        if (!employee.isRegisted()) {
            throw new IllegalArgumentException("통합테스트 인증 대상은 등록된 사원이어야 합니다.");
        }
        String credentialVersion = jwtProvider.createCredentialVersion(employee.getPassword());
        return "Bearer " + jwtProvider.generateToken(
                employee.getEmployeeId(),
                Role.EMPLOYEE.name(),
                credentialVersion);
    }

    protected String adminBearer(Employee employee) {
        if (!employee.isRegisted()) {
            throw new IllegalArgumentException("통합테스트 인증 대상은 등록된 사원이어야 합니다.");
        }
        String credentialVersion = jwtProvider.createCredentialVersion(employee.getPassword());
        return "Bearer " + jwtProvider.generateToken(
                employee.getEmployeeId(),
                Role.ADMIN.name(),
                credentialVersion);
    }

    protected String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("테스트 JSON 직렬화 실패", e);
        }
    }
}
