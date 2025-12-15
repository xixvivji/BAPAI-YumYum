package com.ssafy.bapai.group.controller;

import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.group.dto.GroupDto;
import com.ssafy.bapai.group.dto.GroupRankDto;
import com.ssafy.bapai.group.service.GroupServiceImpl;
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
@RequestMapping("/api/groups") // URL 변경: teams -> groups
@RequiredArgsConstructor
@Tag(name = "5. Group (모임 관리)", description = "모임 생성, 조회, 가입/탈퇴 및 방장 권한 기능(강퇴/위임)")
public class GroupController {

    private final GroupServiceImpl groupService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "모임 생성", description = "새로운 모임을 생성합니다. 생성한 사람은 자동으로 '방장(OWNER)'이 됩니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "생성 완료")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "JWT 토큰 (Bearer ...)", required = true)
            @RequestHeader("Authorization") String token,
            @RequestBody GroupDto groupDto) {
        // leaderId -> ownerId 변경 반영
        groupDto.setOwnerId(jwtUtil.getUserId(token.substring(7)));
        groupService.createGroup(groupDto);
        return ResponseEntity.ok("모임이 생성되었습니다.");
    }

    @GetMapping
    @Operation(summary = "모임 목록 조회 (검색)", description = "키워드로 모임 이름/태그를 검색합니다. (로그인 시 '가입 여부' 포함)")
    public ResponseEntity<List<GroupDto>> list(
            @Parameter(description = "검색어 (모임 이름, 태그)", required = false)
            @RequestParam(required = false) String keyword,

            @Parameter(description = "JWT 토큰 (로그인 한 경우만)", required = false)
            @RequestHeader(value = "Authorization", required = false) String token) {

        Long userId = (token != null) ? jwtUtil.getUserId(token.substring(7)) : null;
        return ResponseEntity.ok(groupService.getList(keyword, userId));
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "모임 상세 조회", description = "특정 모임의 상세 정보와 현재 인원 수 등을 조회합니다.")
    public ResponseEntity<GroupDto> detail(
            @Parameter(description = "모임 ID", required = true)
            @PathVariable Long groupId,

            @Parameter(description = "JWT 토큰 (로그인 한 경우만)", required = false)
            @RequestHeader(value = "Authorization", required = false) String token) {

        Long userId = (token != null) ? jwtUtil.getUserId(token.substring(7)) : null;
        return ResponseEntity.ok(groupService.getDetail(groupId, userId));
    }

    @PostMapping("/{groupId}/join")
    @Operation(summary = "모임 가입 신청", description = "일반 멤버(MEMBER)로 모임에 가입합니다. (중복 가입/정원 초과 시 에러)")
    public ResponseEntity<?> join(
            @Parameter(description = "가입할 모임 ID", required = true)
            @PathVariable Long groupId,

            @Parameter(description = "JWT 토큰", required = true)
            @RequestHeader("Authorization") String token) {

        groupService.joinGroup(groupId, jwtUtil.getUserId(token.substring(7)));
        return ResponseEntity.ok("가입되었습니다.");
    }

    @DeleteMapping("/{groupId}/leave")
    @Operation(summary = "모임 탈퇴", description = "스스로 모임을 나갑니다. (방장은 멤버가 남아있으면 탈퇴 불가, 위임 필요)")
    public ResponseEntity<?> leave(
            @Parameter(description = "탈퇴할 모임 ID", required = true)
            @PathVariable Long groupId,

            @Parameter(description = "JWT 토큰", required = true)
            @RequestHeader("Authorization") String token) {

        groupService.leaveGroup(groupId, jwtUtil.getUserId(token.substring(7)));
        return ResponseEntity.ok("탈퇴하였습니다.");
    }

    @DeleteMapping("/{groupId}/kick/{targetUserId}")
    @Operation(summary = "멤버 강퇴 (방장 전용)", description = "방장이 마음에 안 드는 멤버를 내보냅니다.")
    public ResponseEntity<?> kick(
            @Parameter(description = "모임 ID", required = true)
            @PathVariable Long groupId,

            @Parameter(description = "강퇴할 멤버의 User ID", required = true)
            @PathVariable Long targetUserId,

            @Parameter(description = "JWT 토큰 (방장 본인)", required = true)
            @RequestHeader("Authorization") String token) {

        // Service 메서드 파라미터에 맞게 ownerId(본인) 전달
        groupService.kickMember(groupId, jwtUtil.getUserId(token.substring(7)), targetUserId);
        return ResponseEntity.ok("해당 멤버를 강퇴했습니다.");
    }

    @PutMapping("/{groupId}/delegate/{newOwnerId}")
    @Operation(summary = "방장 권한 위임 (방장 전용)", description = "방장 권한을 다른 멤버에게 넘기고 자신은 일반 멤버가 됩니다.")
    public ResponseEntity<?> delegate(
            @Parameter(description = "모임 ID", required = true)
            @PathVariable Long groupId,

            @Parameter(description = "새로운 방장이 될 멤버의 User ID", required = true)
            @PathVariable Long newOwnerId,

            @Parameter(description = "JWT 토큰 (현재 방장)", required = true)
            @RequestHeader("Authorization") String token) {

        // Service 메서드명 delegateOwner로 변경됨
        groupService.delegateOwner(groupId, jwtUtil.getUserId(token.substring(7)), newOwnerId);
        return ResponseEntity.ok("방장 권한을 위임했습니다.");
    }

    @GetMapping("/{groupId}/ranking")
    @Operation(summary = "모임 내 랭킹 조회 (사이드바)", description = "식단 점수가 높은 상위 5명의 멤버 정보를 반환합니다.")
    public ResponseEntity<List<GroupRankDto>> ranking(
            @Parameter(description = "모임 ID", required = true)
            @PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getGroupRanking(groupId));
    }
}