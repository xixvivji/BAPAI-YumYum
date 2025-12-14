package com.ssafy.bapai.team.controller;

import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.team.dto.TeamDto;
import com.ssafy.bapai.team.dto.TeamRankDto;
import com.ssafy.bapai.team.service.TeamServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Tag(name = "5. Team (모임 관리)", description = "팀 생성, 조회, 가입/탈퇴 및 방장 권한 기능(강퇴/위임)")
public class TeamController {

    private final TeamServiceImpl teamService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "팀 생성", description = "새로운 팀을 생성합니다. 생성한 사람은 자동으로 '방장(LEADER)'이 됩니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "생성 완료")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "JWT 토큰 (Bearer ...)", required = true)
            @RequestHeader("Authorization") String token,
            @RequestBody TeamDto teamDto) {
        teamDto.setLeaderId(jwtUtil.getUserId(token.substring(7)));
        teamService.createTeam(teamDto);
        return ResponseEntity.ok("팀이 생성되었습니다.");
    }

    @GetMapping
    @Operation(summary = "팀 목록 조회 (검색)", description = "키워드로 팀 이름/태그를 검색합니다. (로그인 시 '가입 여부' 포함)")
    public ResponseEntity<List<TeamDto>> list(
            @Parameter(description = "검색어 (팀 이름, 태그)", required = false)
            @RequestParam(required = false) String keyword,

            @Parameter(description = "JWT 토큰 (로그인 한 경우만)", required = false)
            @RequestHeader(value = "Authorization", required = false) String token) {

        Long userId = (token != null) ? jwtUtil.getUserId(token.substring(7)) : null;
        return ResponseEntity.ok(teamService.getList(keyword, userId));
    }

    @GetMapping("/{teamId}")
    @Operation(summary = "팀 상세 조회", description = "특정 팀의 상세 정보와 현재 인원 수 등을 조회합니다.")
    public ResponseEntity<TeamDto> detail(
            @Parameter(description = "팀 ID", required = true)
            @PathVariable Long teamId,

            @Parameter(description = "JWT 토큰 (로그인 한 경우만)", required = false)
            @RequestHeader(value = "Authorization", required = false) String token) {

        Long userId = (token != null) ? jwtUtil.getUserId(token.substring(7)) : null;
        return ResponseEntity.ok(teamService.getDetail(teamId, userId));
    }

    @PostMapping("/{teamId}/join")
    @Operation(summary = "팀 가입 신청", description = "일반 멤버(MEMBER)로 팀에 가입합니다. (중복 가입/정원 초과 시 에러)")
    public ResponseEntity<?> join(
            @Parameter(description = "가입할 팀 ID", required = true)
            @PathVariable Long teamId,

            @Parameter(description = "JWT 토큰", required = true)
            @RequestHeader("Authorization") String token) {

        teamService.joinTeam(teamId, jwtUtil.getUserId(token.substring(7)));
        return ResponseEntity.ok("가입되었습니다.");
    }

    @DeleteMapping("/{teamId}/leave")
    @Operation(summary = "팀 탈퇴", description = "스스로 팀을 나갑니다. (방장은 멤버가 남아있으면 탈퇴 불가, 위임 필요)")
    public ResponseEntity<?> leave(
            @Parameter(description = "탈퇴할 팀 ID", required = true)
            @PathVariable Long teamId,

            @Parameter(description = "JWT 토큰", required = true)
            @RequestHeader("Authorization") String token) {

        teamService.leaveTeam(teamId, jwtUtil.getUserId(token.substring(7)));
        return ResponseEntity.ok("탈퇴하였습니다.");
    }

    @DeleteMapping("/{teamId}/kick/{targetUserId}")
    @Operation(summary = "멤버 강퇴 (방장 전용)", description = "방장이 마음에 안 드는 멤버를 내보냅니다.")
    public ResponseEntity<?> kick(
            @Parameter(description = "팀 ID", required = true)
            @PathVariable Long teamId,

            @Parameter(description = "강퇴할 멤버의 User ID", required = true)
            @PathVariable Long targetUserId,

            @Parameter(description = "JWT 토큰 (방장 본인)", required = true)
            @RequestHeader("Authorization") String token) {

        teamService.kickMember(teamId, jwtUtil.getUserId(token.substring(7)), targetUserId);
        return ResponseEntity.ok("해당 멤버를 강퇴했습니다.");
    }

    @PutMapping("/{teamId}/delegate/{newLeaderId}")
    @Operation(summary = "방장 권한 위임 (방장 전용)", description = "방장 권한을 다른 멤버에게 넘기고 자신은 일반 멤버가 됩니다.")
    public ResponseEntity<?> delegate(
            @Parameter(description = "팀 ID", required = true)
            @PathVariable Long teamId,

            @Parameter(description = "새로운 방장이 될 멤버의 User ID", required = true)
            @PathVariable Long newLeaderId,

            @Parameter(description = "JWT 토큰 (현재 방장)", required = true)
            @RequestHeader("Authorization") String token) {

        teamService.delegateLeader(teamId, jwtUtil.getUserId(token.substring(7)), newLeaderId);
        return ResponseEntity.ok("방장 권한을 위임했습니다.");
    }

    @GetMapping("/{teamId}/ranking")
    @Operation(summary = "팀 내 랭킹 조회 (사이드바)", description = "식단 점수가 높은 상위 5명의 멤버 정보를 반환합니다.")
    public ResponseEntity<List<TeamRankDto>> ranking(
            @Parameter(description = "팀 ID", required = true)
            @PathVariable Long teamId) {
        return ResponseEntity.ok(teamService.getTeamRanking(teamId));
    }
}