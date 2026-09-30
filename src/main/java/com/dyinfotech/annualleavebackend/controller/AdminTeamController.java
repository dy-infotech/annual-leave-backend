package com.dyinfotech.annualleavebackend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.common.security.RequirePersonnelAuthority;
import com.dyinfotech.annualleavebackend.dto.TeamDto;
import com.dyinfotech.annualleavebackend.service.TeamCreateCoordinator;
import com.dyinfotech.annualleavebackend.service.TeamService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "관리자 전용 - 팀 관리", description = "팀 조회/등록/수정 API (대표이사 전용)")
@RestController
@RequestMapping("/api/admin/teams")
@RequirePersonnelAuthority
@RequiredArgsConstructor
public class AdminTeamController {

    private final TeamService teamService;
    private final TeamCreateCoordinator teamCreateCoordinator;

    @Operation(summary = "팀 전체 조회", description = "전체 팀을 담당자, 상위 팀 정보와 함께 조회한다. (소프트 딜리트된 팀 제외)")
    @GetMapping
    public ResponseEntity<List<TeamDto.TeamResponse>> getTeams(@AuthenticationPrincipal EmployeePrincipal principal) {
        return ResponseEntity.ok(teamService.findAllForAdmin());
    }

    @Operation(summary = "팀 등록", description = "새로운 팀을 등록한다. 담당자는 필수이며, 상위 팀 미지정 시 대표이사 팀을 기본 상위 팀으로 사용한다.")
    @PostMapping
    public ResponseEntity<TeamDto.CreateResponse> createTeam(
            @AuthenticationPrincipal EmployeePrincipal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TeamDto.CreateRequest request) {
        Long teamId = teamCreateCoordinator.createTeam(
                principal.employeeId(),
                request,
                idempotencyKey);
        return ResponseEntity.ok(TeamDto.CreateResponse.builder()
                .teamId(teamId)
                .build());
    }

    @Operation(summary = "팀 수정", description = "팀명/담당자/상위 팀/소속 부서를 변경한다. null인 필드는 기존 값을 유지한다.")
    @PutMapping("/{teamId}")
    public ResponseEntity<Void> updateTeam(
            @AuthenticationPrincipal EmployeePrincipal principal,
            @PathVariable("teamId") Long teamId,
            @Valid @RequestBody TeamDto.UpdateRequest request) {
        teamService.updateTeam(teamId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "팀 삭제", description = "팀을 소프트 딜리트한다. 하위 팀이나 재직 중인 소속 사원이 있으면 삭제할 수 없다.")
    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> deleteTeam(
            @AuthenticationPrincipal EmployeePrincipal principal,
            @PathVariable("teamId") Long teamId) {
        teamService.deleteTeam(teamId);
        return ResponseEntity.ok().build();
    }
}
