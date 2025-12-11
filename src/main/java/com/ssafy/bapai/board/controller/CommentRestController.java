package com.ssafy.bapai.board.controller;

import com.ssafy.bapai.board.dto.CommentDto;
import com.ssafy.bapai.board.service.CommentService;
import com.ssafy.bapai.common.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Tag(name = "4. 댓글 API", description = "댓글, 대댓글, 댓글 추천 기능")
public class CommentRestController {

    private final CommentService commentService;
    private final JwtUtil jwtUtil;

    // 1. 특정 게시글의 댓글 목록 조회
    @Operation(summary = "댓글 목록 조회", description = "특정 게시글의 댓글을 계층형 구조(대댓글 포함)로 조회합니다.")
    @GetMapping("/board/{boardId}")
    public ResponseEntity<List<CommentDto>> getComments(
            @Parameter(description = "게시글 ID", example = "1")
            @PathVariable Long boardId) {
        return ResponseEntity.ok(commentService.getComments(boardId));
    }

    // 2. 댓글 작성
    @Operation(summary = "댓글 작성", description = "게시글에 댓글을 답니다. parentId가 있으면 대댓글이 됩니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "작성할 댓글 정보 (boardId 필수, 대댓글일 경우 parentId 포함)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommentDto.class),
                    examples = {
                            @ExampleObject(name = "1. 일반 댓글 작성 예시", value = """
                                    {
                                      "boardId": 1,
                                      "content": "이 식단 정말 맛있어 보이네요!",
                                      "parentId": null
                                    }
                                    """),
                            @ExampleObject(name = "2. 대댓글(답글) 작성 예시", value = """
                                    {
                                      "boardId": 1,
                                      "content": "감사합니다! 꼭 드셔보세요.",
                                      "parentId": 10
                                    }
                                    """)
                    }
            )
    )
    @PostMapping
    public ResponseEntity<?> write(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @RequestBody CommentDto dto) {

        Long userId = jwtUtil.getUserId(token.substring(7));
        dto.setUserId(userId);

        commentService.writeComment(dto);
        return ResponseEntity.ok("댓글 등록 완료");
    }

    // 3. 댓글 추천/비추천
    @Operation(summary = "댓글 추천/비추천", description = "댓글에 좋아요(LIKE) 또는 싫어요(DISLIKE)를 누릅니다.")
    @PostMapping("/{commentId}/reaction")
    public ResponseEntity<?> reaction(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long commentId,
            @Parameter(description = "반응 유형 (LIKE 또는 DISLIKE)", example = "LIKE", required = true)
            @RequestParam String type) {

        Long userId = jwtUtil.getUserId(token.substring(7));
        try {
            commentService.addReaction(commentId, userId, type);
            return ResponseEntity.ok("반영되었습니다.");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}