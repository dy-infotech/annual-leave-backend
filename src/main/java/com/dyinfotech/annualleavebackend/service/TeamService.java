package com.dyinfotech.annualleavebackend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dyinfotech.annualleavebackend.common.cache.OrganizationCacheInvalidator;
import com.dyinfotech.annualleavebackend.common.type.ManageType;
import com.dyinfotech.annualleavebackend.common.type.PositionType;
import com.dyinfotech.annualleavebackend.config.CacheConfig;
import com.dyinfotech.annualleavebackend.domain.Department;
import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.domain.Team;
import com.dyinfotech.annualleavebackend.domain.TeamManager;
import com.dyinfotech.annualleavebackend.domain.TeamManager.TeamManagerId;
import com.dyinfotech.annualleavebackend.dto.TeamDto;
import com.dyinfotech.annualleavebackend.repository.DepartmentRepository;
import com.dyinfotech.annualleavebackend.repository.EmployeeRepository;
import com.dyinfotech.annualleavebackend.repository.TeamManagerRepository;
import com.dyinfotech.annualleavebackend.repository.TeamRepository;
import com.dyinfotech.annualleavebackend.repository.projection.DepartmentCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamCacheRow;
import com.dyinfotech.annualleavebackend.repository.projection.TeamManagerCacheRow;
import com.github.benmanes.caffeine.cache.LoadingCache;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TeamService {

    public record ManagedTeam(
            Long teamId,
            String teamName,
            Long parentTeamId,
            String parentTeamName,
            Long projectManagerId,
            String managerEmployeeNumber,
            String managerName,
            String managerPosition,
            LocalDate managerFireDate) {

        public boolean isManagerActive(LocalDate today) {
            return managerFireDate == null || !managerFireDate.isBefore(today);
        }
    }

    @Qualifier("teamLoadingCache")
    private final LoadingCache<String, List<TeamCacheRow>> teamCache;
    @Qualifier("teamManagerLoadingCache")
    private final LoadingCache<String, List<TeamManagerCacheRow>> teamManagerCache;
    @Qualifier("departmentLoadingCache")
    private final LoadingCache<String, List<DepartmentCacheRow>> departmentCache;

    private final TeamRepository teamRepository;
    private final TeamManagerRepository teamManagerRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final OrganizationCacheInvalidator cacheInvalidator;
    private final Clock clock;

    public TeamService(
            @Qualifier("teamLoadingCache") LoadingCache<String, List<TeamCacheRow>> teamCache,
            @Qualifier("teamManagerLoadingCache") LoadingCache<String, List<TeamManagerCacheRow>> teamManagerCache,
            @Qualifier("departmentLoadingCache") LoadingCache<String, List<DepartmentCacheRow>> departmentCache,
            TeamRepository teamRepository,
            TeamManagerRepository teamManagerRepository,
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            OrganizationCacheInvalidator cacheInvalidator,
            Clock clock) {
        this.teamCache = teamCache;
        this.teamManagerCache = teamManagerCache;
        this.departmentCache = departmentCache;
        this.teamRepository = teamRepository;
        this.teamManagerRepository = teamManagerRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.cacheInvalidator = cacheInvalidator;
        this.clock = clock;
    }

    public Optional<TeamCacheRow> findTeamInfo(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return Optional.empty();
        }
        return teamCache.get(teamName).stream().findFirst();
    }

    public List<TeamCacheRow> findAllTeamInfo() {
        return teamCache.get(CacheConfig.TOTAL_KEY);
    }

    public Optional<TeamCacheRow> findTeamInfo(Long teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        return teamCache.get(CacheConfig.TOTAL_KEY).stream()
                .filter(team -> team.teamId().equals(teamId))
                .findFirst();
    }

    /**
     * 쓰기 로직에서 실제 JPA 엔티티가 필요할 때만 조회한다.
     */
    public Optional<Team> findByTeamName(String teamName) {
        return findTeamInfo(teamName)
                .flatMap(team -> teamRepository.findById(team.teamId()));
    }

    private List<TeamManagerCacheRow> findManagerRows(Long teamId) {
        if (teamId == null) {
            return Collections.emptyList();
        }
        return teamManagerCache.get(String.valueOf(teamId));
    }

    private Map<Long, TeamCacheRow> teamIndex() {
        return teamCache.get(CacheConfig.TOTAL_KEY).stream()
                .collect(Collectors.toMap(TeamCacheRow::teamId, team -> team));
    }

    private ManagedTeam toManagedTeam(TeamManagerCacheRow manager, Map<Long, TeamCacheRow> teams) {
        TeamCacheRow team = teams.get(manager.teamId());
        TeamCacheRow parent = teams.get(manager.parentTeamId());
        if (team == null || parent == null) {
            return null;
        }

        return new ManagedTeam(
                team.teamId(),
                team.teamName(),
                parent.teamId(),
                parent.teamName(),
                manager.projectManagerId(),
                manager.employeeNumber(),
                manager.managerName(),
                manager.position(),
                manager.fireDate());
    }

    public List<ManagedTeam> findAll() {
        LocalDate today = LocalDate.now(clock);
        Map<Long, TeamCacheRow> teams = teamIndex();
        return teamManagerCache.get(CacheConfig.TOTAL_KEY).stream()
                .filter(manager -> manager.isActive(today))
                .map(manager -> toManagedTeam(manager, teams))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<ManagedTeam> findAllByTeam(String teamName) {
        Optional<TeamCacheRow> team = findTeamInfo(teamName);
        if (team.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDate today = LocalDate.now(clock);
        Map<Long, TeamCacheRow> teams = teamIndex();
        return findManagerRows(team.get().teamId()).stream()
                .filter(manager -> manager.isActive(today))
                .map(manager -> toManagedTeam(manager, teams))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<ManagedTeam> findManagedTeams(Long employeeId) {
        if (employeeId == null) {
            return Collections.emptyList();
        }
        return findAll().stream()
                .filter(team -> employeeId.equals(team.projectManagerId()))
                .toList();
    }

    public Set<Long> findAllProjectManagerIds() {
        return findAll().stream()
                .map(ManagedTeam::projectManagerId)
                .collect(Collectors.toSet());
    }

    public Set<Long> findAllProjectManagerIds(Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Set.of();
        }

        return findAll().stream()
                .map(ManagedTeam::projectManagerId)
                .filter(employeeIds::contains)
                .collect(Collectors.toSet());
    }

    public boolean existsByProjectManager_EmployeeId(Long projectManagerId) {
        return isTeamManager(projectManagerId);
    }

    public boolean isTeamManager(Long employeeId) {
        return !findManagedTeams(employeeId).isEmpty();
    }

    public Set<ManagedTeam> getSelfAndDescendants(String targetTeam) {
        Optional<TeamCacheRow> target = findTeamInfo(targetTeam);
        if (target.isEmpty()) {
            return Collections.emptySet();
        }

        // 조직 관계는 PM 재직 여부와 분리한다. 퇴사 PM row도 parent-child 간선을 유지한다.
        Map<Long, TeamCacheRow> teams = teamIndex();
        List<ManagedTeam> hierarchyRows = teamManagerCache.get(CacheConfig.TOTAL_KEY).stream()
                .map(manager -> toManagedTeam(manager, teams))
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, List<ManagedTeam>> managersByTeam = hierarchyRows.stream()
                .collect(Collectors.groupingBy(ManagedTeam::teamId));

        Map<Long, Set<Long>> childrenByParent = new HashMap<>();
        for (ManagedTeam manager : hierarchyRows) {
            childrenByParent
                    .computeIfAbsent(manager.parentTeamId(), ignored -> new LinkedHashSet<>())
                    .add(manager.teamId());
        }

        Set<Long> visited = new HashSet<>();
        Set<ManagedTeam> result = new LinkedHashSet<>();
        traverseDown(target.get().teamId(), managersByTeam, childrenByParent, visited, result);
        return result;
    }

    private void traverseDown(
            Long teamId,
            Map<Long, List<ManagedTeam>> managersByTeam,
            Map<Long, Set<Long>> childrenByParent,
            Set<Long> visited,
            Set<ManagedTeam> result) {
        if (!visited.add(teamId)) {
            return;
        }

        result.addAll(managersByTeam.getOrDefault(teamId, Collections.emptyList()));
        for (Long childTeamId : childrenByParent.getOrDefault(teamId, Collections.emptySet())) {
            if (!childTeamId.equals(teamId)) {
                traverseDown(childTeamId, managersByTeam, childrenByParent, visited, result);
            }
        }
    }

    private List<String> getAllAncestors(String targetTeam) {
        Optional<TeamCacheRow> target = findTeamInfo(targetTeam);
        if (target.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, TeamCacheRow> teams = teamIndex();
        List<String> ancestors = new ArrayList<>();
        Long currentTeamId = target.get().teamId();
        Set<Long> visited = new HashSet<>();

        while (currentTeamId != null && visited.add(currentTeamId)) {
            TeamCacheRow current = teams.get(currentTeamId);
            if (current == null) {
                break;
            }
            ancestors.add(current.teamName());

            List<TeamManagerCacheRow> managers = findManagerRows(currentTeamId);
            if (managers.isEmpty()) {
                break;
            }

            Long parentId = managers.get(0).parentTeamId();
            if (parentId == null || parentId.equals(currentTeamId)) {
                break;
            }
            currentTeamId = parentId;
        }
        return ancestors;
    }

    private Map.Entry<Integer, String> getTeamManagerData(
            PositionType approverPosition,
            String targetTeam,
            Long approverId) {
        int manageType = 0;
        boolean isCEO = PositionType.isCEO(approverPosition);

        Optional<TeamCacheRow> targetTeamInfo = findTeamInfo(targetTeam);
        List<ManagedTeam> approverTeamList = findManagedTeams(approverId);
        List<String> managedTeamNames = approverTeamList.stream()
                .map(ManagedTeam::teamName)
                .distinct()
                .toList();

        // 팀 존재 여부는 Team 캐시로 판단한다. 관리자 없는 팀을 신규 팀으로 오판하지 않는다.
        if (targetTeamInfo.isEmpty()) {
            if (isCEO) {
                return new AbstractMap.SimpleEntry<>(ManageType.IS_NEW_TEAM.addFlag(manageType), "");
            }
            return new AbstractMap.SimpleEntry<>(manageType, String.join(",", managedTeamNames));
        }

        if (isCEO) {
            return new AbstractMap.SimpleEntry<>(
                    ManageType.IS_TEAM_MANAGER.addFlag(manageType),
                    String.join(",", managedTeamNames));
        }

        List<String> ancestors = getAllAncestors(targetTeam);
        if (ancestors.stream().anyMatch(managedTeamNames::contains)) {
            manageType = ManageType.IS_TEAM_MANAGER.addFlag(manageType);
        }

        return new AbstractMap.SimpleEntry<>(manageType, String.join(",", managedTeamNames));
    }

    public Map.Entry<Integer, String> getTeamManagerData(String targetTeam, Employee approver) {
        return getTeamManagerData(
                PositionType.getType(approver.getPosition()),
                targetTeam,
                approver.getEmployeeId());
    }

    private Set<Employee> resolveApprovers(Employee employee) {
        Long employeeTeamId = employee.getTeamId();
        if (employeeTeamId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "팀 정보를 찾을 수 없습니다.");
        }

        LocalDate today = LocalDate.now(clock);
        List<TeamManagerCacheRow> myTeam = findManagerRows(employeeTeamId).stream()
                .filter(manager -> manager.isActive(today))
                .toList();

        if (myTeam.isEmpty()) {
            log.error("TeamService::resolveApprovers - 해당 팀의 재직 관리자가 존재하지 않습니다. team_id : {}", employeeTeamId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "팀의 재직 관리자가 존재하지 않습니다.");
        }

        Optional<TeamManagerCacheRow> selfManager = myTeam.stream()
                .filter(manager -> employee.getEmployeeId().equals(manager.projectManagerId()))
                .findFirst();

        Set<Long> approverIds;
        if (selfManager.isPresent()) {
            Long parentTeamId = selfManager.get().parentTeamId();
            List<TeamManagerCacheRow> parentManagers = parentTeamId.equals(employeeTeamId)
                    ? myTeam
                    : findManagerRows(parentTeamId).stream()
                            .filter(parent -> parent.isActive(today))
                            .toList();

            if (parentManagers.isEmpty()) {
                log.error("TeamService::resolveApprovers - 상위 팀의 재직 관리자가 존재하지 않습니다. parent_team_id : {}", parentTeamId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "상위 팀의 재직 관리자가 존재하지 않습니다.");
            }

            approverIds = parentManagers.stream()
                    .map(TeamManagerCacheRow::projectManagerId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } else {
            approverIds = myTeam.stream()
                    .map(TeamManagerCacheRow::projectManagerId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        return employeeRepository.findAllById(approverIds).stream()
                .filter(candidate -> candidate.isActive(today))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<Long> refreshApproverIds(Employee employee) {
        Set<Employee> resolvedApprovers = resolveApprovers(employee);
        boolean hasApproverId = resolvedApprovers.stream()
                .anyMatch(approver -> approver.getEmployeeId().equals(employee.getApproverId()));

        if (!hasApproverId && !resolvedApprovers.isEmpty()) {
            employee.changeApprover(resolvedApprovers.iterator().next());
        }

        return resolvedApprovers.stream()
                .map(Employee::getEmployeeId)
                .collect(Collectors.toSet());
    }

    @Transactional
    public void saveTeam(TeamManager teamManager) {
        teamManagerRepository.save(teamManager);
        cacheInvalidator.afterTeamManagerChange(Set.of(teamManager.getTeamId()));
    }

    @Transactional
    public void saveTeam(Team team) {
        teamRepository.save(team);
        cacheInvalidator.afterTeamChange(Set.of(team.getTeamName()), false);
    }

    @Transactional
    public void deleteTeam(TeamManager teamManager) {
        Long teamId = teamManager.getTeamId();
        teamManagerRepository.delete(teamManager);
        cacheInvalidator.afterTeamManagerChange(Set.of(teamId));
    }

    private Team lockTeam(Long teamId) {
        return teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "팀 정보를 찾을 수 없습니다."));
    }

    private Map<Long, Team> lockTeams(Collection<Long> teamIds) {
        Map<Long, Team> locked = new HashMap<>();
        if (teamIds == null) {
            return locked;
        }

        teamIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(teamId -> locked.put(teamId, lockTeam(teamId)));
        return locked;
    }

    public boolean hasActiveManager(Long teamId) {
        return teamId != null
                && teamManagerRepository.existsActiveManagerInTeam(teamId, LocalDate.now(clock));
    }

    @Transactional
    public void requireActiveManager(Long teamId) {
        lockTeam(teamId);
        if (!hasActiveManager(teamId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "재직 중인 담당자가 없는 팀에는 사원을 배정할 수 없습니다. 담당자를 먼저 지정해주세요.");
        }
    }

    @Transactional
    public void validateManagerDeactivation(Long employeeId, LocalDate fireDate) {
        if (fireDate == null) {
            return;
        }

        LocalDate inactiveFrom = fireDate.equals(LocalDate.MAX) ? fireDate : fireDate.plusDays(1);
        List<Long> initialTeamIds = teamManagerRepository.findTeamIdsByProjectManagerId(employeeId).stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        lockTeams(initialTeamIds);
        employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        List<Long> currentTeamIds = teamManagerRepository.findTeamIdsByProjectManagerId(employeeId).stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (!initialTeamIds.equals(currentTeamIds)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "담당 팀 정보가 동시에 변경되었습니다. 다시 시도해주세요.");
        }

        for (Long teamId : currentTeamIds) {
            if (!teamManagerRepository.existsOtherActiveManagerInTeam(teamId, employeeId, inactiveFrom)
                    && hasApprovalDependentsAfterDeactivation(teamId, employeeId, inactiveFrom)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "예약 퇴사일 이후 결재 공백이 발생합니다. 다른 재직 담당자를 먼저 지정해주세요.");
            }
        }
    }

    private boolean hasApprovalDependents(Long teamId, LocalDate today) {
        return employeeRepository.existsActiveEmployeeInTeam(teamId, today)
                || teamManagerRepository.existsByParentTeam_TeamIdAndTeam_TeamIdNot(teamId, teamId);
    }

    private boolean hasApprovalDependentsAfterDeactivation(Long teamId, Long employeeId, LocalDate inactiveFrom) {
        return employeeRepository.existsActiveEmployeeInTeamExcludingEmployee(teamId, employeeId, inactiveFrom)
                || teamManagerRepository.existsByParentTeam_TeamIdAndTeam_TeamIdNot(teamId, teamId);
    }

    private void validateManager(Employee manager) {
        if (!manager.isActive(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "퇴사 처리된 사원은 팀 담당자로 지정할 수 없습니다.");
        }
    }

    private void validateParentTeam(Team team, Team parentTeam) {
        if (!Boolean.TRUE.equals(parentTeam.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비활성화된 팀은 상위 팀으로 지정할 수 없습니다.");
        }
        if (!parentTeam.getTeamId().equals(team.getTeamId()) && !hasActiveManager(parentTeam.getTeamId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "재직 중인 담당자가 없는 팀은 상위 팀으로 지정할 수 없습니다.");
        }
    }

    @Transactional
    public void removeManager(Long teamId, Long employeeId) {
        lockTeam(teamId);
        TeamManagerId id = new TeamManagerId(teamId, employeeId);
        if (!teamManagerRepository.existsById(id)) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        if (!teamManagerRepository.existsOtherActiveManagerInTeam(teamId, employeeId, today)
                && hasApprovalDependents(teamId, today)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "활성 사원 또는 하위 팀이 의존하는 팀의 마지막 재직 담당자는 해제할 수 없습니다.");
        }

        teamManagerRepository.deleteById(id);
        cacheInvalidator.afterTeamManagerChange(Set.of(teamId));
    }

    @Transactional
    public void addManager(String teamName, Long employeeId, Long parentTeamId) {
        TeamCacheRow teamInfo = findTeamInfo(teamName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "팀 정보가 잘못되었습니다."));
        Map<Long, Team> lockedTeams = lockTeams(List.of(teamInfo.teamId(), parentTeamId));
        Team team = lockedTeams.get(teamInfo.teamId());
        Team parent = lockedTeams.get(parentTeamId);
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        validateManager(employee);
        validateParentTeam(team, parent);
        if (!parent.getTeamId().equals(team.getTeamId())) {
            validateNoCycle(team.getTeamId(), parent);
        }

        List<TeamManager> currentManagers = teamManagerRepository.findAllByTeam_TeamId(team.getTeamId());
        if (currentManagers.stream().anyMatch(manager -> !manager.getParentTeamId().equals(parentTeamId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동일 팀의 담당자들은 같은 상위 팀을 사용해야 합니다.");
        }

        teamManagerRepository.save(TeamManager.builder()
                .team(team)
                .projectManager(employee)
                .parentTeam(parent)
                .build());
        cacheInvalidator.afterTeamManagerChange(Set.of(teamInfo.teamId()));
    }

    public Optional<Long> resolveParentTeamId(String teamName) {
        return findTeamInfo(teamName)
                .flatMap(team -> findManagerRows(team.teamId()).stream().findFirst())
                .map(TeamManagerCacheRow::parentTeamId);
    }

    public List<TeamDto.TeamResponse> findAllForAdmin() {
        Map<Long, DepartmentCacheRow> departments = departmentCache.get(CacheConfig.TOTAL_KEY).stream()
                .collect(Collectors.toMap(DepartmentCacheRow::departmentId, department -> department));
        Map<Long, TeamCacheRow> teams = teamIndex();
        Map<Long, List<TeamManagerCacheRow>> managersByTeam = teamManagerCache.get(CacheConfig.TOTAL_KEY).stream()
                .collect(Collectors.groupingBy(TeamManagerCacheRow::teamId));

        return teams.values().stream()
                .map(team -> {
                    DepartmentCacheRow department = departments.get(team.departmentId());
                    List<TeamManagerCacheRow> managers = managersByTeam.getOrDefault(team.teamId(), Collections.emptyList());
                    TeamManagerCacheRow first = managers.isEmpty() ? null : managers.get(0);
                    TeamCacheRow parent = first == null ? null : teams.get(first.parentTeamId());

                    return TeamDto.TeamResponse.builder()
                            .teamId(team.teamId())
                            .teamName(team.teamName())
                            .enabled(team.enabled())
                            .departmentId(team.departmentId())
                            .departmentName(department != null ? department.departmentName() : null)
                            .parentTeamId(first != null ? first.parentTeamId() : null)
                            .parentTeamName(parent != null ? parent.teamName() : null)
                            .managers(managers.stream()
                                    .map(manager -> TeamDto.ManagerResponse.builder()
                                            .employeeId(manager.projectManagerId())
                                            .employeeNumber(manager.employeeNumber())
                                            .name(manager.managerName())
                                            .position(manager.position())
                                            .build())
                                    .toList())
                            .build();
                })
                .toList();
    }

    @Transactional
    public Long createTeam(Long requesterId, TeamDto.CreateRequest request) {
        String teamName = request.getTeamName().trim();
        if (teamRepository.findByTeamName(teamName).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 팀명입니다.");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "소속 부서가 존재하지 않습니다."));
        if (!Boolean.TRUE.equals(department.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비활성화된 부서에는 팀을 생성할 수 없습니다.");
        }

        if (request.getProjectManagerId() == null && request.getParentTeamId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "담당자가 없는 팀은 상위 팀 결재선을 지정할 수 없습니다.");
        }

        Team team = Team.builder()
                .teamName(teamName)
                .enabled(Boolean.TRUE)
                .department(department)
                .build();

        try {
            teamRepository.saveAndFlush(team);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 팀명입니다.");
        }

        cacheInvalidator.afterTeamChange(Set.of(teamName), false);

        if (request.getProjectManagerId() != null) {
            Employee manager;

            Long parentTeamId = request.getParentTeamId();
            if (parentTeamId == null) {
                Employee requester = employeeRepository.findById(requesterId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "요청자 정보를 찾을 수 없습니다."));
                parentTeamId = requester.getTeamId();
            }

            Team parentTeam = lockTeam(parentTeamId);
            manager = employeeRepository.findByIdForUpdate(request.getProjectManagerId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "담당자로 지정할 사원이 존재하지 않습니다."));
            validateManager(manager);
            validateParentTeam(team, parentTeam);
            teamManagerRepository.save(TeamManager.builder()
                    .team(team)
                    .projectManager(manager)
                    .parentTeam(parentTeam)
                    .build());
            cacheInvalidator.afterTeamManagerChange(Set.of(team.getTeamId()));
        }

        return team.getTeamId();
    }

    @Transactional
    public void updateTeam(Long teamId, TeamDto.UpdateRequest request) {
        Map<Long, Team> lockedTeams = lockTeams(request.getParentTeamId() == null
                ? List.of(teamId)
                : List.of(teamId, request.getParentTeamId()));
        Team team = lockedTeams.get(teamId);
        if (!Boolean.TRUE.equals(team.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "팀 정보를 찾을 수 없습니다.");
        }

        String oldTeamName = team.getTeamName();
        boolean teamChanged = false;
        boolean employeeViewChanged = false;
        boolean managerChanged = false;

        if (request.getTeamName() != null && !request.getTeamName().isBlank()) {
            String newName = request.getTeamName().trim();
            if (!newName.equals(oldTeamName)) {
                if (teamRepository.findByTeamName(newName).isPresent()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 팀명입니다.");
                }
                team.changeName(newName);
                teamChanged = true;
                employeeViewChanged = true;
            }
        }

        if (request.getDepartmentId() != null) {
            Department newDepartment = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "소속 부서가 존재하지 않습니다."));
            if (!Boolean.TRUE.equals(newDepartment.getEnabled())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비활성화된 부서로는 변경할 수 없습니다.");
            }
            if (!newDepartment.getDepartmentId().equals(team.getDepartment().getDepartmentId())) {
                team.changeDepartment(newDepartment);
                for (Employee member : employeeRepository.findAllByTeam_TeamId(teamId)) {
                    member.changeDepartment(newDepartment);
                }
                teamChanged = true;
                employeeViewChanged = true;
            }
        }

        List<TeamManager> currentManagers = teamManagerRepository.findAllByTeam_TeamId(teamId);
        Team newParentTeam = null;
        if (request.getParentTeamId() != null) {
            if (request.getParentTeamId().equals(teamId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신을 상위 팀으로 지정할 수 없습니다.");
            }
            newParentTeam = lockedTeams.get(request.getParentTeamId());
            if (newParentTeam == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상위 팀이 존재하지 않습니다.");
            }
            validateParentTeam(team, newParentTeam);
            validateNoCycle(teamId, newParentTeam);
        }

        if (request.getProjectManagerId() != null) {
            Employee manager = employeeRepository.findByIdForUpdate(request.getProjectManagerId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "담당자로 지정할 사원이 존재하지 않습니다."));
            validateManager(manager);

            Team parentTeam = newParentTeam;
            if (parentTeam == null) {
                if (currentManagers.isEmpty()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "담당자 정보가 없는 팀입니다. 상위 팀을 함께 지정해주세요.");
                }
                parentTeam = currentManagers.get(0).getParentTeam();
            }
            validateParentTeam(team, parentTeam);

            teamManagerRepository.deleteAll(currentManagers);
            teamManagerRepository.flush();
            teamManagerRepository.save(TeamManager.builder()
                    .team(team)
                    .projectManager(manager)
                    .parentTeam(parentTeam)
                    .build());
            managerChanged = true;
        } else if (newParentTeam != null) {
            if (currentManagers.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "담당자가 없는 팀은 상위 팀 결재선을 지정할 수 없습니다.");
            }
            for (TeamManager teamManager : currentManagers) {
                teamManager.changeParentTeam(newParentTeam);
            }
            managerChanged = true;
        }

        try {
            teamRepository.flush();
            teamManagerRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 팀명입니다.");
        }

        if (teamChanged) {
            cacheInvalidator.afterTeamChange(new LinkedHashSet<>(List.of(oldTeamName, team.getTeamName())), employeeViewChanged);
        }
        if (managerChanged) {
            cacheInvalidator.afterTeamManagerChange(Set.of(teamId));
        }
    }

    private void validateNoCycle(Long teamId, Team newParent) {
        Long currentId = newParent.getTeamId();
        Set<Long> visited = new HashSet<>();

        while (currentId != null && visited.add(currentId)) {
            if (currentId.equals(teamId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "해당 팀의 하위 팀은 상위 팀으로 지정할 수 없습니다.");
            }

            List<TeamManager> rows = teamManagerRepository.findAllByTeam_TeamId(currentId);
            if (rows.isEmpty()) {
                break;
            }

            Long parentId = rows.get(0).getParentTeamId();
            if (parentId == null || parentId.equals(currentId)) {
                break;
            }
            currentId = parentId;
        }
    }

    @Transactional
    public void deleteTeam(Long teamId) {
        Team team = lockTeam(teamId);
        if (!Boolean.TRUE.equals(team.getEnabled())) {
            return;
        }

        if (teamManagerRepository.existsByParentTeam_TeamIdAndTeam_TeamIdNot(teamId, teamId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "하위 팀이 있는 팀은 삭제할 수 없습니다. 하위 팀을 먼저 정리해주세요.");
        }

        if (employeeRepository.existsActiveEmployeeInTeam(teamId, LocalDate.now(clock))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "소속 사원이 있는 팀은 삭제할 수 없습니다. 사원의 팀을 먼저 변경해주세요.");
        }

        String teamName = team.getTeamName();
        teamManagerRepository.deleteByTeam_TeamId(teamId);
        team.disable();

        cacheInvalidator.afterTeamChange(Set.of(teamName), false);
        cacheInvalidator.afterTeamManagerChange(Set.of(teamId));
    }
}
