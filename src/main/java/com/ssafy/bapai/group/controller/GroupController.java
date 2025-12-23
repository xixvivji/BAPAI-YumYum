package com.ssafy.bapai.group.controller;

import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.group.dto.GroupDto;
import com.ssafy.bapai.group.dto.GroupRankDto;
import com.ssafy.bapai.group.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
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
    @Operation(summary = "모임 생성")
    public ResponseEntity<?> create(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @RequestBody GroupDto groupDto) {
        groupDto.setOwnerId(jwtUtil.getUserId(token.substring(7)));
        groupService.createGroup(groupDto);
        return ResponseEntity.ok(Map.of("message", "모임이 생성되었습니다."));
    }


    @GetMapping
    @Operation(summary = "모임 목록 조회 (페이지네이션)")
    public ResponseEntity<List<GroupDto>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page, // 페이지 번호
            @RequestParam(defaultValue = "12") int size, // 페이지 당 개수
            @RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = (token != null) ? jwtUtil.getUserId(token.substring(7)) : null;
        return ResponseEntity.ok(groupService.getList(keyword, page, size, userId));
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
        return ResponseEntity.ok(Map.of("message", "가입되었습니다."));
    }

    @DeleteMapping("/{groupId}/leave")
    @Operation(summary = "모임 탈퇴")
    public ResponseEntity<?> leave(
            @PathVariable Long groupId,
            @RequestHeader("Authorization") String token) {
        groupService.leaveGroup(groupId, jwtUtil.getUserId(token.substring(7)));
        return ResponseEntity.ok(Map.of("message", "탈퇴하였습니다."));
    }

    @DeleteMapping("/{groupId}/kick/{targetUserId}")
    @Operation(summary = "멤버 강퇴")
    public ResponseEntity<?> kick(
            @PathVariable Long groupId,
            @PathVariable Long targetUserId,
            @RequestHeader("Authorization") String token) {
        groupService.kickMember(groupId, jwtUtil.getUserId(token.substring(7)), targetUserId);
        return ResponseEntity.ok(Map.of("message", "해당 멤버를 강퇴했습니다."));
    }

    @PutMapping("/{groupId}/delegate/{newOwnerId}")
    @Operation(summary = "방장 권한 위임")
    public ResponseEntity<?> delegate(
            @PathVariable Long groupId,
            @PathVariable Long newOwnerId,
            @RequestHeader("Authorization") String token) {
        groupService.delegateOwner(groupId, jwtUtil.getUserId(token.substring(7)), newOwnerId);
        return ResponseEntity.ok(Map.of("message", "방장 권한을 위임했습니다."));
    }

    @GetMapping("/{groupId}/ranking")
    @Operation(summary = "모임 내 랭킹 조회")
    public ResponseEntity<List<GroupRankDto>> ranking(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getGroupRanking(groupId));
    }

    @GetMapping("/tags")
    @Operation(summary = "해시태그 목록 조회")
    public ResponseEntity<List<String>> getTags(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(groupService.getHashtagList(keyword));
    }

    @Operation(summary = "내가 가입한 그룹 조회")
    @GetMapping("/me")
    public ResponseEntity<List<GroupDto>> getMyGroups(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        return ResponseEntity.ok(groupService.getMyGroups(userId));
    }

    @PostMapping("/{groupId}/invite/{targetUserId}")
    @Operation(summary = "멤버 초대하기")
    public ResponseEntity<?> invite(
            @PathVariable Long groupId,
            @PathVariable Long targetUserId,
            @RequestHeader("Authorization") String token) {
        Long ownerId = jwtUtil.getUserId(token.substring(7));
        groupService.inviteMember(groupId, ownerId, targetUserId); // API 추가
        return ResponseEntity.ok(Map.of("message", "사용자를 초대하였습니다."));
    }
}