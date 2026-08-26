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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dyinfotech.annualleavebackend.common.security.EmployeePrincipal;
import com.dyinfotech.annualleavebackend.dto.TeamDto;
import com.dyinfotech.annualleavebackend.service.AuthService;
import com.dyinfotech.annualleavebackend.service.TeamService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "관리자 전용 - 팀 관리", description = "팀 조회/등록/수정 API (대표이사 전용)")
@RestController
@RequestMapping("/api/admin/teams")
@RequiredArgsConstructor
public class AdminTeamController {

    private final AuthService authService;
    private final TeamService teamService;

    @Operation(summary = "팀 전체 조회", description = "전체 팀을 담당자, 상위 팀 정보와 함께 조회한다. (소프트 딜리트된 팀 제외)")
    @GetMapping
    public ResponseEntity<List<TeamDto.TeamResponse>> getTeams(@AuthenticationPrincipal EmployeePrincipal principal) {
    	authService.checkPersonnelAuthority(principal.employeeId());
    	return ResponseEntity.ok(teamService.findAllForAdmin());
    }

    @Operation(summary = "팀 등록", description = "새로운 팀을 등록한다. 담당자(projectManagerId) 지정이 필수이며, 상위 팀 미지정 시 요청자(대표이사)의 팀이 상위 팀이 된다. 중복된 팀명은 409를 반환한다.")
    @PostMapping
    public ResponseEntity<TeamDto.CreateResponse> createTeam(@AuthenticationPrincipal EmployeePrincipal principal, 
    														@Valid @RequestBody TeamDto.CreateRequest request) {
    	authService.checkPersonnelAuthority(principal.employeeId());
    	Long teamId = teamService.createTeam(principal.employeeId(), request);
    	return ResponseEntity.ok(TeamDto.CreateResponse.builder()
    													.teamId(teamId)
    													.build());
    }

    @Operation(summary = "팀 수정", description = "팀명/담당자/상위 팀을 변경한다. null인 필드는 기존 값을 유지하며, 담당자 지정 시 기존 담당자 전원이 교체된다.")
    @PutMapping("/{teamId}")
    public ResponseEntity<Void> updateTeam(@AuthenticationPrincipal EmployeePrincipal principal, 
    										@PathVariable("teamId") Long teamId, 
    										@Valid @RequestBody TeamDto.UpdateRequest request) {
    	authService.checkPersonnelAuthority(principal.employeeId());
    	teamService.updateTeam(teamId, request);
    	return ResponseEntity.ok().build();
    }

    @Operation(summary = "팀 삭제", description = "팀을 소프트 딜리트한다. 하위 팀이나 재직 중인 소속 사원이 있으면 삭제할 수 없으며, 결재선 정보(담당자)도 함께 제거된다.")
    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> deleteTeam(@AuthenticationPrincipal EmployeePrincipal principal, 
    										@PathVariable("teamId") Long teamId) {
    	authService.checkPersonnelAuthority(principal.employeeId());
    	teamService.deleteTeam(teamId);
    	return ResponseEntity.ok().build();
    }

}
