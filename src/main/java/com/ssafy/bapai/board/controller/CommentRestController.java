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
import java.util.Map;
import lombok.Data;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Tag(name = "4. 댓글 API", description = "댓글 CRUD 및 좋아요/싫어요 기능")
public class CommentRestController {

    private final CommentService commentService;
    private final JwtUtil jwtUtil;

    // 특정 게시글의 댓글 목록 조회
    @Operation(summary = "댓글 목록 조회", description = "로그인한 경우 본인의 추천 여부(userReaction)도 함께 반환됩니다.")
    @GetMapping("/board/{boardId}")
    public ResponseEntity<List<CommentDto>> getComments(
            @PathVariable Long boardId,
            // 토큰은 있을 수도 있고 없을 수도 (required = false)
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false)
            String token) {

        Long userId = null;

        // 토큰이 있고 유효하다면 userId 추출
        if (token != null && token.startsWith("Bearer ")) {
            try {
                userId = jwtUtil.getUserId(token.substring(7));
            } catch (Exception e) {
                // 토큰이 만료되었거나 이상하면 그냥 비회원 취급
                userId = null;
            }
        }

        return ResponseEntity.ok(commentService.getComments(boardId, userId));
    }

    // 댓글 작성
    @Operation(summary = "댓글 작성", description = "게시글에 댓글을 답니다. parentId가 있으면 대댓글이 됩니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "작성할 댓글 정보", required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommentDto.class),
                    examples = @ExampleObject(value = "{\"boardId\": 1, \"content\": \"댓글 내용\", \"parentId\": null}")
            )
    )
    @PostMapping
    public ResponseEntity<?> write(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @RequestBody CommentDto dto) {

        dto.setUserId(getUserIdFromToken(token));
        commentService.writeComment(dto);
        return ResponseEntity.ok(Map.of("message", "댓글이 등록되었습니다."));
    }


    // 댓글 수정 추가

    @Operation(summary = "댓글 수정", description = "내 댓글 내용을 수정합니다.")
    @PutMapping("/{commentId}")
    public ResponseEntity<?> update(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long commentId,
            @RequestBody CommentDto dto) {

        Long userId = getUserIdFromToken(token);
        dto.setCommentId(commentId);
        dto.setUserId(userId); // 본인 확인용

        try {
            commentService.modifyComment(dto);
            return ResponseEntity.ok(Map.of("message", "댓글이 수정되었습니다."));
        } catch (SecurityException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }


    //  댓글 삭제 추가
    @Operation(summary = "댓글 삭제", description = "내 댓글을 삭제합니다.")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<?> delete(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long commentId) {

        Long userId = getUserIdFromToken(token);
        try {
            commentService.removeComment(commentId, userId);
            return ResponseEntity.ok(Map.of("message", "댓글이 삭제되었습니다."));
        } catch (SecurityException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }


    //  댓글 추천/비추천 등록 Body 방식

    @Operation(summary = "댓글 추천/비추천 등록", description = "Body에 { \"type\": \"LIKE\" } 형태로 전송")
    @PostMapping("/{commentId}/reaction")
    public ResponseEntity<?> addReaction(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long commentId,
            @RequestBody ReactionRequestDto requestDto) { // Body로 받기

        String type = requestDto.getType();

        // 유효성 검사
        if (type == null || (!type.equals("LIKE") && !type.equals("DISLIKE"))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "잘못된 type입니다. (LIKE 또는 DISLIKE)"));
        }

        Long userId = getUserIdFromToken(token);
        try {
            commentService.addReaction(commentId, userId, type);
            return ResponseEntity.ok(Map.of("message", "반영되었습니다."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }


    // 댓글 추천/비추천 취소
    @Operation(summary = "댓글 추천/비추천 취소", description = "기존에 눌렀던 좋아요/싫어요를 취소합니다.")
    @DeleteMapping("/{commentId}/reaction")
    public ResponseEntity<?> cancelReaction(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long commentId) {

        Long userId = getUserIdFromToken(token);
        try {
            commentService.deleteReaction(commentId, userId);
            return ResponseEntity.ok(Map.of("message", "취소되었습니다."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Helper 토큰 파싱
    private Long getUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtil.getUserId(token.substring(7));
        }
        throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
    }

    //요청용 내부 DTO
    @Data
    public static class ReactionRequestDto {
        private String type; // LIKE or DISLIKE
    }
}