package com.ssafy.bapai.team.controller;

import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.team.dto.TeamBoardDto;
import com.ssafy.bapai.team.service.TeamBoardServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams/{teamId}/posts")
@RequiredArgsConstructor
@Tag(name = "6. Team Board (팀 게시판)", description = "팀 내 공지사항 및 자유게시판")
public class TeamBoardController {

    private final TeamBoardServiceImpl teamBoardService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "게시글 작성", description = "type: 'NOTICE'(공지, 방장만 가능), 'FREE'(자유, 멤버 누구나 가능)")
    public ResponseEntity<?> write(
            @Parameter(description = "팀 ID", required = true)
            @PathVariable Long teamId,

            @Parameter(description = "JWT 토큰", required = true)
            @RequestHeader("Authorization") String token,

            @RequestBody TeamBoardDto dto) {

        dto.setTeamId(teamId);
        dto.setUserId(jwtUtil.getUserId(token.substring(7)));
        teamBoardService.writeBoard(dto);
        return ResponseEntity.ok("게시글이 등록되었습니다.");
    }

    @GetMapping
    @Operation(summary = "게시글 목록 조회", description = "공지사항(NOTICE)이 상단에 고정되고, 나머지는 최신순으로 정렬됩니다.")
    public ResponseEntity<List<TeamBoardDto>> list(
            @Parameter(description = "팀 ID", required = true)
            @PathVariable Long teamId) {
        return ResponseEntity.ok(teamBoardService.getList(teamId));
    }

    @GetMapping("/{tbId}")
    @Operation(summary = "게시글 상세 조회", description = "게시글 내용을 조회합니다. 본인이 작성자인지 여부(isWriter)를 포함합니다.")
    public ResponseEntity<TeamBoardDto> detail(
            @Parameter(description = "팀 ID", required = true)
            @PathVariable Long teamId,

            @Parameter(description = "게시글 ID (PK)", required = true)
            @PathVariable Long tbId,

            @Parameter(description = "JWT 토큰 (로그인 시 본인 글 확인용)", required = false)
            @RequestHeader(value = "Authorization", required = false) String token) {

        Long userId = (token != null) ? jwtUtil.getUserId(token.substring(7)) : null;
        return ResponseEntity.ok(teamBoardService.getDetail(tbId, userId));
    }

    @DeleteMapping("/{tbId}")
    @Operation(summary = "게시글 삭제", description = "작성자 본인 또는 방장만 삭제할 수 있습니다.")
    public ResponseEntity<?> delete(
            @Parameter(description = "팀 ID", required = true)
            @PathVariable Long teamId,

            @Parameter(description = "삭제할 게시글 ID", required = true)
            @PathVariable Long tbId,

            @Parameter(description = "JWT 토큰", required = true)
            @RequestHeader("Authorization") String token) {

        teamBoardService.deleteBoard(tbId, jwtUtil.getUserId(token.substring(7)));
        return ResponseEntity.ok("게시글이 삭제되었습니다.");
    }
}