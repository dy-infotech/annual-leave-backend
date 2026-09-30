package com.dyinfotech.annualleavebackend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
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

    private List<TeamManagerCacheRow> findManagerRowsWithDelta(
            Long teamId,
            Employee mutatedManager,
            Set<Long> removedManagerTeamIds,
            Map<Long, Long> addedManagerParentTeamIds) {
        List<TeamManagerCacheRow> rows = new ArrayList<>(findManagerRows(teamId));
        if (mutatedManager == null || mutatedManager.getEmployeeId() == null) {
            return rows;
        }

        Long managerId = mutatedManager.getEmployeeId();
        boolean removed = removedManagerTeamIds.contains(teamId);
        boolean added = addedManagerParentTeamIds.containsKey(teamId);
        if (!removed && !added) {
            return rows;
        }

        // 같은 transaction의 미커밋 TeamManager 변경은 committed cache snapshot에 request delta만 합성한다.
        rows.removeIf(row -> managerId.equals(row.projectManagerId()));
        if (added) {
            rows.add(new TeamManagerCacheRow(
                    teamId,
                    managerId,
                    addedManagerParentTeamIds.get(teamId),
                    mutatedManager.getEmployeeNumber(),
                    mutatedManager.getName(),
                    mutatedManager.getPosition(),
                    mutatedManager.getHireDate(),
                    mutatedManager.getFireDate()
            ));
        }
        return rows;
    }

    private Set<Long> resolveApproverIds(Employee employee) {
        return resolveApproverIds(employee, Set.of(), Map.of());
    }

    private Set<Long> resolveApproverIds(
            Employee employee,
            Collection<Long> removedManagerTeamIds,
            Map<Long, Long> addedManagerParentTeamIds) {
        Long employeeTeamId = employee.getTeamId();
        if (employeeTeamId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "팀 정보를 찾을 수 없습니다.");
        }

        Set<Long> removedTeamIds = removedManagerTeamIds == null
                ? Set.of()
                : removedManagerTeamIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<Long, Long> addedParentByTeamId = addedManagerParentTeamIds == null
                ? Map.of()
                : addedManagerParentTeamIds.entrySet().stream()
                        .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        LocalDate today = LocalDate.now(clock);
        List<TeamManagerCacheRow> myTeam = findManagerRowsWithDelta(
                employeeTeamId,
                employee,
                removedTeamIds,
                addedParentByTeamId
        ).stream()
                .filter(manager -> manager.isActive(today))
                .toList();

        if (myTeam.isEmpty()) {
            log.error("TeamService::resolveApproverIds - 해당 팀의 재직 관리자가 존재하지 않습니다. team_id : {}", employeeTeamId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "팀의 재직 관리자가 존재하지 않습니다.");
        }

        Optional<TeamManagerCacheRow> selfManager = myTeam.stream()
                .filter(manager -> employee.getEmployeeId().equals(manager.projectManagerId()))
                .findFirst();

        if (selfManager.isPresent()) {
            Long parentTeamId = selfManager.get().parentTeamId();
            List<TeamManagerCacheRow> parentManagers = parentTeamId.equals(employeeTeamId)
                    ? myTeam
                    : findManagerRowsWithDelta(
                            parentTeamId,
                            employee,
                            removedTeamIds,
                            addedParentByTeamId
                    ).stream()
                            .filter(parent -> parent.isActive(today))
                            .toList();

            if (parentManagers.isEmpty()) {
                log.error("TeamService::resolveApproverIds - 상위 팀의 재직 관리자가 존재하지 않습니다. parent_team_id : {}", parentTeamId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "상위 팀의 재직 관리자가 존재하지 않습니다.");
            }

            return parentManagers.stream()
                    .map(TeamManagerCacheRow::projectManagerId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        return myTeam.stream()
                .map(TeamManagerCacheRow::projectManagerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 현재 조직 기준 결재자 ID 집합.
     * 저장된 approver_id나 Team/TeamManager DB 재조회에 의존하지 않고 최신 조직 캐시 snapshot만 사용한다.
     */
    public Set<Long> resolveCurrentApproverIds(Employee employee) {
        return resolveApproverIds(employee);
    }

    public Employee resolveCurrentApprover(Employee employee) {
        Set<Long> approverIds = resolveApproverIds(employee);
        Long storedApproverId = employee.getApproverId();
        Long currentApproverId = storedApproverId != null && approverIds.contains(storedApproverId)
                ? storedApproverId
                : approverIds.stream()
                        .min(Long::compareTo)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "현재 조직 기준 결재자를 찾을 수 없습니다."));

        // 결재자 선정 자체는 조직 캐시에서 끝낸다. DB 조회는 /me 표시용 Employee 1건 materialize 용도다.
        return employeeRepository.findById(currentApproverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "현재 결재자 정보를 찾을 수 없습니다."));
    }

    /**
     * 저장된 approver_id self-heal 전용. 권한 검증에서는 사용하지 않는다.
     */
    public Set<Long> refreshApproverIds(Employee employee) {
        return refreshApproverIds(employee, Set.of(), Map.of());
    }

    /**
     * 같은 transaction에서 TeamManager가 함께 바뀌는 write path용 self-heal.
     * committed Caffeine snapshot에 이번 request의 add/remove delta만 합성하며 DB fallback은 하지 않는다.
     */
    public Set<Long> refreshApproverIds(
            Employee employee,
            Collection<Long> removedManagerTeamIds,
            Map<Long, Long> addedManagerParentTeamIds) {
        Set<Long> approverIds = resolveApproverIds(
                employee,
                removedManagerTeamIds,
                addedManagerParentTeamIds
        );
        Long storedApproverId = employee.getApproverId();
        if ((storedApproverId == null || !approverIds.contains(storedApproverId)) && !approverIds.isEmpty()) {
            Long currentApproverId = approverIds.stream().min(Long::compareTo).orElseThrow();
            employee.changeApprover(employeeRepository.getReferenceById(currentApproverId));
        }
        return approverIds;
    }

    @Transactional
    public void saveTeam(TeamManager teamManager) {
        Long teamId = teamManager.getTeamId();
        Long parentTeamId = teamManager.getParentTeamId();
        Long managerId = teamManager.getProjectManagerId();
        Map<Long, Team> lockedTeams = lockTeams(List.of(teamId, parentTeamId));
        Team team = lockedTeams.get(teamId);
        Team parent = lockedTeams.get(parentTeamId);
        Employee manager = employeeRepository.findByIdForUpdate(managerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "담당자로 지정할 사원이 존재하지 않습니다."));

        validateManager(manager);
        validateParentTeam(team, parent);
        List<TeamManager> currentManagers = teamManagerRepository.findAllByTeam_TeamId(teamId);
        if (currentManagers.stream().anyMatch(row -> !row.getParentTeamId().equals(parentTeamId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동일 팀의 담당자들은 같은 상위 팀을 사용해야 합니다.");
        }

        teamManagerRepository.save(TeamManager.builder()
                .team(team)
                .projectManager(manager)
                .parentTeam(parent)
                .build());
        teamManagerRepository.flush();
        validateFutureApprovalCoverageLocked(teamId);
        validateFutureApprovalCoverageLocked(parentTeamId);
        cacheInvalidator.afterTeamManagerChange(Set.of(teamId));
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

    @Transactional
    public void lockTeamsForUpdate(Collection<Long> teamIds) {
        lockTeams(teamIds);
    }

    @Transactional
    public void validateFutureApprovalCoverage(Collection<Long> teamIds) {
        Map<Long, Team> locked = lockTeams(teamIds);
        locked.keySet().stream()
                .sorted()
                .forEach(this::validateFutureApprovalCoverageLocked);
    }

    private void validateFutureApprovalCoverageLocked(Long teamId) {
        LocalDate today = LocalDate.now(clock);
        boolean hasChildTeam = teamManagerRepository
                .existsByParentTeam_TeamIdAndTeam_TeamIdNot(teamId, teamId);

        List<Employee> dependents = employeeRepository.findAllByTeam_TeamId(teamId).stream()
                .filter(employee -> employee.isActive(today))
                .toList();

        if (!hasChildTeam && dependents.isEmpty()) {
            return;
        }

        List<Employee> activeManagers = teamManagerRepository.findAllByTeam_TeamId(teamId).stream()
                .map(TeamManager::getProjectManager)
                .filter(manager -> manager.isActive(today))
                .toList();

        if (activeManagers.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현재 또는 미래 결재 의존성이 있는 팀에는 재직 담당자가 필요합니다.");
        }

        boolean dependentOpenEnded = hasChildTeam || dependents.stream()
                .anyMatch(employee -> isOpenEnded(employee.getFireDate()));
        boolean managerOpenEnded = activeManagers.stream()
                .anyMatch(manager -> isOpenEnded(manager.getFireDate()));

        if (dependentOpenEnded) {
            if (!managerOpenEnded) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "예약 퇴사 이후 미래 결재 공백이 발생합니다. 퇴사 예정이 없는 담당자를 지정해주세요.");
            }
            return;
        }

        if (managerOpenEnded) {
            return;
        }

        LocalDate dependentThrough = dependents.stream()
                .map(Employee::getFireDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(today);
        LocalDate managerThrough = activeManagers.stream()
                .map(Employee::getFireDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(today.minusDays(1));

        if (managerThrough.isBefore(dependentThrough)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 퇴사 이후 미래 결재 공백이 발생합니다. 담당자 재직 기간을 확인해주세요.");
        }
    }

    private boolean isOpenEnded(LocalDate fireDate) {
        return fireDate == null || LocalDate.MAX.equals(fireDate);
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

        teamManagerRepository.deleteById(id);
        teamManagerRepository.flush();
        validateFutureApprovalCoverageLocked(teamId);
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
        Optional<TeamManager> existingManager = currentManagers.stream()
                .filter(manager -> employeeId.equals(manager.getProjectManagerId()))
                .findFirst();
        if (existingManager.isPresent()) {
            if (!parentTeamId.equals(existingManager.get().getParentTeamId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "이미 해당 팀의 담당자이지만 상위 팀 정보가 다릅니다.");
            }
            // 동일한 add 요청은 상태를 다시 쓰지 않고 성공 처리한다.
            validateFutureApprovalCoverageLocked(team.getTeamId());
            validateFutureApprovalCoverageLocked(parent.getTeamId());
            return;
        }

        if (currentManagers.stream().anyMatch(manager -> !manager.getParentTeamId().equals(parentTeamId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동일 팀의 담당자들은 같은 상위 팀을 사용해야 합니다.");
        }

        teamManagerRepository.save(TeamManager.builder()
                .team(team)
                .projectManager(employee)
                .parentTeam(parent)
                .build());
        teamManagerRepository.flush();
        validateFutureApprovalCoverageLocked(team.getTeamId());
        validateFutureApprovalCoverageLocked(parent.getTeamId());
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
        return createTeam(requesterId, request, null);
    }

    @Transactional
    public Long createTeam(
            Long requesterId,
            TeamDto.CreateRequest request,
            String idempotencyKey) {
        String teamName = request.getTeamName().trim();
        String normalizedRequestKey = normalizeCreateRequestKey(idempotencyKey);
        String requestHash = normalizedRequestKey == null
                ? null
                : createTeamRequestHash(requesterId, request, teamName);

        if (normalizedRequestKey != null) {
            Optional<Team> replay = teamRepository.findByCreateRequestKey(normalizedRequestKey);
            if (replay.isPresent()) {
                if (!java.util.Objects.equals(replay.get().getCreateRequestHash(), requestHash)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "동일한 Idempotency-Key가 다른 팀 생성 요청에 사용되었습니다.");
                }
                return replay.get().getTeamId();
            }
        }

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
        if (normalizedRequestKey != null) {
            team.markCreateRequest(normalizedRequestKey, requestHash);
        }

        try {
            teamRepository.saveAndFlush(team);
        } catch (DataIntegrityViolationException e) {
            if (normalizedRequestKey != null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "동일한 팀 생성 요청이 동시에 처리되었습니다. 같은 Idempotency-Key로 다시 요청해주세요.");
            }
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

            Map<Long, Team> lockedTeams = lockTeams(List.of(team.getTeamId(), parentTeamId));
            team = lockedTeams.get(team.getTeamId());
            Team parentTeam = lockedTeams.get(parentTeamId);
            manager = employeeRepository.findByIdForUpdate(request.getProjectManagerId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "담당자로 지정할 사원이 존재하지 않습니다."));
            validateManager(manager);
            validateParentTeam(team, parentTeam);
            teamManagerRepository.save(TeamManager.builder()
                    .team(team)
                    .projectManager(manager)
                    .parentTeam(parentTeam)
                    .build());
            teamManagerRepository.flush();
            validateFutureApprovalCoverageLocked(team.getTeamId());
            validateFutureApprovalCoverageLocked(parentTeam.getTeamId());
            cacheInvalidator.afterTeamManagerChange(Set.of(team.getTeamId()));
        }

        return team.getTeamId();
    }

    private String normalizeCreateRequestKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }

        String normalized = idempotencyKey.trim();
        if (normalized.length() < 16
                || normalized.length() > 128
                || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String createTeamRequestHash(
            Long requesterId,
            TeamDto.CreateRequest request,
            String normalizedTeamName) {
        String canonical = requesterId
                + "|" + normalizedTeamName.length() + ":" + normalizedTeamName
                + "|" + String.valueOf(request.getDepartmentId())
                + "|" + String.valueOf(request.getProjectManagerId())
                + "|" + String.valueOf(request.getParentTeamId());

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    @Transactional
    public void updateTeam(Long teamId, TeamDto.UpdateRequest request) {
        Long plannedParentTeamId = request.getParentTeamId();
        if (plannedParentTeamId == null && request.getProjectManagerId() != null) {
            plannedParentTeamId = teamManagerRepository.findAllByTeam_TeamId(teamId).stream()
                    .findFirst()
                    .map(TeamManager::getParentTeamId)
                    .orElse(null);
        }

        List<Long> teamIdsToLock = new ArrayList<>();
        teamIdsToLock.add(teamId);
        if (plannedParentTeamId != null) {
            teamIdsToLock.add(plannedParentTeamId);
        }
        Map<Long, Team> lockedTeams = lockTeams(teamIdsToLock);
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
        if (request.getProjectManagerId() != null && request.getParentTeamId() == null
                && !currentManagers.isEmpty()
                && !java.util.Objects.equals(plannedParentTeamId, currentManagers.get(0).getParentTeamId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "팀의 상위 조직 정보가 동시에 변경되었습니다. 다시 시도해주세요.");
        }

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

        Team finalParentTeam = newParentTeam;
        if (request.getProjectManagerId() != null) {
            Employee manager = employeeRepository.findByIdForUpdate(request.getProjectManagerId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "담당자로 지정할 사원이 존재하지 않습니다."));
            validateManager(manager);

            if (finalParentTeam == null) {
                if (currentManagers.isEmpty()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "담당자 정보가 없는 팀입니다. 상위 팀을 함께 지정해주세요.");
                }
                Long currentParentId = currentManagers.get(0).getParentTeamId();
                finalParentTeam = lockedTeams.get(currentParentId);
                if (finalParentTeam == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "팀의 상위 조직 정보가 동시에 변경되었습니다. 다시 시도해주세요.");
                }
            }
            validateParentTeam(team, finalParentTeam);

            boolean alreadyDesiredManagerState = currentManagers.size() == 1
                    && request.getProjectManagerId().equals(currentManagers.get(0).getProjectManagerId())
                    && finalParentTeam.getTeamId().equals(currentManagers.get(0).getParentTeamId());
            if (!alreadyDesiredManagerState) {
                teamManagerRepository.deleteAll(currentManagers);
                teamManagerRepository.flush();
                teamManagerRepository.save(TeamManager.builder()
                        .team(team)
                        .projectManager(manager)
                        .parentTeam(finalParentTeam)
                        .build());
                managerChanged = true;
            }
        } else if (newParentTeam != null) {
            if (currentManagers.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "담당자가 없는 팀은 상위 팀 결재선을 지정할 수 없습니다.");
            }
            Long desiredParentTeamId = newParentTeam.getTeamId();
            boolean alreadyDesiredParent = currentManagers.stream()
                    .allMatch(teamManager -> desiredParentTeamId.equals(teamManager.getParentTeamId()));
            if (!alreadyDesiredParent) {
                for (TeamManager teamManager : currentManagers) {
                    teamManager.changeParentTeam(newParentTeam);
                }
                managerChanged = true;
            }
        }

        try {
            teamRepository.flush();
            teamManagerRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 팀명입니다.");
        }

        if (managerChanged) {
            validateFutureApprovalCoverageLocked(teamId);
            if (finalParentTeam != null) {
                validateFutureApprovalCoverageLocked(finalParentTeam.getTeamId());
            }
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
