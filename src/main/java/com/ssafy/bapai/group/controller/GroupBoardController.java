package com.ssafy.bapai.group.controller;

import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.group.dto.GroupBoardDto;
import com.ssafy.bapai.group.service.GroupBoardServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups/{groupId}/posts")
@RequiredArgsConstructor
@Tag(name = "6. Group Board (모임 게시판)", description = "모임 내 공지사항 및 자유게시판")
public class GroupBoardController {

    private final GroupBoardServiceImpl groupBoardService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "게시글 작성", description = "type: 'NOTICE'(공지), 'FREE'(자유)")
    public ResponseEntity<?> write(
            @PathVariable Long groupId,
            @RequestHeader("Authorization") String token,
            @RequestBody GroupBoardDto dto) {

        dto.setGroupId(groupId);
        dto.setUserId(jwtUtil.getUserId(token.substring(7)));
        groupBoardService.writeBoard(dto);
        return ResponseEntity.ok("게시글이 등록되었습니다.");
    }

    @GetMapping
    @Operation(summary = "게시글 목록 조회")
    public ResponseEntity<List<GroupBoardDto>> list(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupBoardService.getList(groupId));
    }

    @GetMapping("/{boardId}") // boardId
    @Operation(summary = "게시글 상세 조회")
    public ResponseEntity<GroupBoardDto> detail(
            @PathVariable Long groupId,
            @PathVariable Long boardId,
            @RequestHeader(value = "Authorization", required = false) String token) {

        Long userId = (token != null) ? jwtUtil.getUserId(token.substring(7)) : null;
        return ResponseEntity.ok(groupBoardService.getDetail(boardId, userId));
    }

    @DeleteMapping("/{boardId}") // boardId
    @Operation(summary = "게시글 삭제")
    public ResponseEntity<?> delete(
            @PathVariable Long groupId,
            @PathVariable Long boardId,
            @RequestHeader("Authorization") String token) {

        groupBoardService.deleteBoard(boardId, jwtUtil.getUserId(token.substring(7)));
        return ResponseEntity.ok("게시글이 삭제되었습니다.");
    }
}