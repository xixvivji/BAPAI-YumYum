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
import java.util.HashMap;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Tag(name = "4. 댓글 API", description = "댓글 CRUD 및 좋아요/싫어요 기능")
public class CommentRestController {

    private final CommentService commentService;
    private final JwtUtil jwtUtil;

    // 1. 댓글 목록 조회 (페이징 + 정렬 적용)
    @Operation(summary = "댓글 목록 조회", description = "옵션: page(기본 1), size(기본 10), sort(latest, oldest, likes)")
    @GetMapping("/board/{boardId}")
    public ResponseEntity<Map<String, Object>> getComments(
            @PathVariable Long boardId,

            // ★ 추가된 파라미터들
            @Parameter(description = "페이지 번호 (1부터 시작)")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "한 페이지당 개수")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "정렬 기준: latest(최신순), oldest(오래된순), likes(좋아요순)")
            @RequestParam(defaultValue = "latest") String sort,

            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false)
            String token) {

        Long userId = null;
        if (token != null && token.startsWith("Bearer ")) {
            try {
                userId = jwtUtil.getUserId(token.substring(7));
            } catch (Exception e) {
                userId = null;
            }
        }

        // 1. 오프셋 계산 (MySQL Limit용)
        int offset = (page - 1) * size;

        // 2. 서비스 호출 (목록 + 전체 개수)
        // 서비스 메서드를 getComments -> getCommentList로 변경 필요 (파라미터 추가)
        List<CommentDto> list = commentService.getCommentList(boardId, userId, sort, size, offset);
        int totalCount = commentService.getCommentCount(boardId);

        // 3. 결과 맵핑
        Map<String, Object> response = new HashMap<>();
        response.put("comments", list);
        response.put("totalCount", totalCount);
        response.put("currentPage", page);
        response.put("hasNext", (page * size) < totalCount); // 다음 페이지 있는지 계산

        return ResponseEntity.ok(response);
    }

    // ... (아래 작성, 수정, 삭제, 좋아요 코드는 기존과 동일하므로 생략하지 않고 그대로 둡니다) ...

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

    @Operation(summary = "댓글 수정", description = "내 댓글 내용을 수정합니다.")
    @PutMapping("/{commentId}")
    public ResponseEntity<?> update(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long commentId,
            @RequestBody CommentDto dto) {

        Long userId = getUserIdFromToken(token);
        dto.setCommentId(commentId);
        dto.setUserId(userId);

        try {
            commentService.modifyComment(dto);
            return ResponseEntity.ok(Map.of("message", "댓글이 수정되었습니다."));
        } catch (SecurityException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

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

    @Operation(summary = "댓글 추천/비추천 등록", description = "Body에 { \"type\": \"LIKE\" } 형태로 전송")
    @PostMapping("/{commentId}/reaction")
    public ResponseEntity<?> addReaction(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long commentId,
            @RequestBody ReactionRequestDto requestDto) {

        String type = requestDto.getType();
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

    private Long getUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtil.getUserId(token.substring(7));
        }
        throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
    }

    @Data
    public static class ReactionRequestDto {
        private String type;
    }
}