package com.ssafy.bapai.group.controller;

import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.group.dto.GroupDto;
import com.ssafy.bapai.group.dto.GroupRankDto;
import com.ssafy.bapai.group.service.GroupService;
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
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "5. Group (모임 관리)", description = "모임 생성, 조회, 가입/탈퇴 및 방장 권한 기능")
public class GroupController {

    private final GroupService groupService; // ★ 인터페이스 주입
    private final JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "모임 생성", description = "새로운 모임을 생성합니다. (이미지 없이 텍스트 정보만 입력)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "생성 완료")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "JWT 토큰", required = true)
            @RequestHeader("Authorization") String token,
            @RequestBody GroupDto groupDto) {

        // 토큰에서 방장 ID 추출
        groupDto.setOwnerId(jwtUtil.getUserId(token.substring(7)));

        groupService.createGroup(groupDto);
        return ResponseEntity.ok("모임이 생성되었습니다.");
    }

    @GetMapping
    @Operation(summary = "모임 목록 조회 (검색)", description = "키워드로 모임 이름/태그를 검색합니다.")
    public ResponseEntity<List<GroupDto>> list(
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {

        Long userId = (token != null) ? jwtUtil.getUserId(token.substring(7)) : null;
        return ResponseEntity.ok(groupService.getList(keyword, userId));
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "모임 상세 조회")
    public ResponseEntity<GroupDto> detail(
            @PathVariable Long groupId,
            @RequestHeader(value = "Authorization", required = false) String token) {

        Long userId = (token != null) ? jwtUtil.getUserId(token.substring(7)) : null;
        return ResponseEntity.ok(groupService.getDetail(groupId, userId));
    }

    @PostMapping("/{groupId}/join")
    @Operation(summary = "모임 가입 신청")
    public ResponseEntity<?> join(
            @PathVariable Long groupId,
            @RequestHeader("Authorization") String token) {

        groupService.joinGroup(groupId, jwtUtil.getUserId(token.substring(7)));
        return ResponseEntity.ok("가입되었습니다.");
    }

    @DeleteMapping("/{groupId}/leave")
    @Operation(summary = "모임 탈퇴")
    public ResponseEntity<?> leave(
            @PathVariable Long groupId,
            @RequestHeader("Authorization") String token) {

        groupService.leaveGroup(groupId, jwtUtil.getUserId(token.substring(7)));
        return ResponseEntity.ok("탈퇴하였습니다.");
    }

    @DeleteMapping("/{groupId}/kick/{targetUserId}")
    @Operation(summary = "멤버 강퇴 (방장 전용)")
    public ResponseEntity<?> kick(
            @PathVariable Long groupId,
            @PathVariable Long targetUserId,
            @RequestHeader("Authorization") String token) {

        groupService.kickMember(groupId, jwtUtil.getUserId(token.substring(7)), targetUserId);
        return ResponseEntity.ok("해당 멤버를 강퇴했습니다.");
    }

    @PutMapping("/{groupId}/delegate/{newOwnerId}")
    @Operation(summary = "방장 권한 위임 (방장 전용)")
    public ResponseEntity<?> delegate(
            @PathVariable Long groupId,
            @PathVariable Long newOwnerId,
            @RequestHeader("Authorization") String token) {

        groupService.delegateOwner(groupId, jwtUtil.getUserId(token.substring(7)), newOwnerId);
        return ResponseEntity.ok("방장 권한을 위임했습니다.");
    }

    @GetMapping("/{groupId}/ranking")
    @Operation(summary = "모임 내 랭킹 조회 (Top 5)")
    public ResponseEntity<List<GroupRankDto>> ranking(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getGroupRanking(groupId));
    }

    @GetMapping("/tags")
    @Operation(summary = "해시태그 목록 조회 (자동완성)", description = "키워드를 입력하면 포함된 태그 목록을 반환합니다. (입력 없으면 전체 반환)")
    public ResponseEntity<List<String>> getTags(
            @Parameter(description = "검색할 태그명 (생략 시 전체)")
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(groupService.getHashtagList(keyword));
    }
}