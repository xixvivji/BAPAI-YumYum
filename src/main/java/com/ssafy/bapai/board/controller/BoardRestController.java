package com.ssafy.bapai.board.controller;

import com.ssafy.bapai.board.dto.BoardDto;
import com.ssafy.bapai.board.service.BoardService;
import com.ssafy.bapai.board.service.CommentService;
import com.ssafy.bapai.common.dto.PageResponse;
import com.ssafy.bapai.common.s3.S3Service;
import com.ssafy.bapai.common.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
@Tag(name = "3. 게시판 API", description = "자유/리뷰/전문가 게시판 CRUD 및 추천 기능")
public class BoardRestController {

    private final S3Service s3Service; // S3 주입됨
    private final BoardService boardService;
    private final CommentService commentService;
    private final JwtUtil jwtUtil;

    // 게시글 목록 조회
    @Operation(summary = "게시글 목록 조회", description = "로그인 시 userLiked(본인 반응) 포함")
    @GetMapping
    public ResponseEntity<PageResponse<BoardDto>> getList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String word,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false)
            String token) {

        Long userId = getUserIdIfExist(token);
        return ResponseEntity.ok(
                boardService.getBoardList(page, size, category, key, word, userId));
    }

    // 게시글 상세 조회
    @Operation(summary = "게시글 상세 조회", description = "로그인 시 userLiked 포함")
    @GetMapping("/{boardId}")
    public ResponseEntity<BoardDto> getDetail(
            @PathVariable Long boardId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false)
            String token) {

        Long userId = getUserIdIfExist(token);
        return ResponseEntity.ok(boardService.getBoardDetail(boardId, userId));
    }

    // 글 작성 (S3 적용 완료)
    @Operation(summary = "게시글 작성", description = "이미지 파일을 포함하여 게시글을 작성합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> write(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @ModelAttribute BoardDto boardDto,
            @RequestPart(value = "image", required = false) MultipartFile file) {

        Long userId = jwtUtil.getUserId(token.substring(7));
        boardDto.setUserId(userId);

        try {

            if (file != null && !file.isEmpty()) {
                String imgUrl = s3Service.uploadFile(file, "board"); // "board" 폴더에 저장
                boardDto.setImgUrl(imgUrl);
            }
            boardService.writeBoard(boardDto);
            return ResponseEntity.ok(Map.of("message", "게시글이 등록되었습니다."));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("이미지 업로드 실패");
        }
    }

    // 글 수정 (S3 적용 완료)
    @Operation(summary = "게시글 수정")
    @PutMapping(value = "/{boardId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long boardId,
            @ModelAttribute BoardDto boardDto,
            @RequestPart(value = "image", required = false) MultipartFile file) {

        Long userId = jwtUtil.getUserId(token.substring(7));

        try {

            if (file != null && !file.isEmpty()) {
                String imgUrl = s3Service.uploadFile(file, "board");
                boardDto.setImgUrl(imgUrl);
            }

            boardDto.setBoardId(boardId);
            boardDto.setUserId(userId);
            boardService.modifyBoard(boardDto);

            return ResponseEntity.ok(Map.of("message", "게시글이 수정되었습니다."));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("이미지 업로드 실패");
        }
    }

    // 글 삭제
    @Operation(summary = "게시글 삭제")
    @DeleteMapping("/{boardId}")
    public ResponseEntity<?> delete(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @Parameter(description = "삭제할 게시글 ID", example = "1")
            @PathVariable Long boardId) {

        Long userId = getUserIdFromToken(token);
        try {
            boardService.removeBoard(boardId, userId);
            return ResponseEntity.ok(Map.of("message", "게시글이 삭제되었습니다."));
        } catch (SecurityException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 추천/비추천
    @Operation(summary = "게시글 추천/비추천 등록")
    @PostMapping("/{boardId}/reaction")
    public ResponseEntity<?> addReaction(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long boardId,
            @RequestBody ReactionRequestDto requestDto) {

        String type = requestDto.getType();
        if (!"LIKE".equals(type) && !"DISLIKE".equals(type)) {
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 type입니다."));
        }

        Long userId = getUserIdFromToken(token);
        boardService.addBoardReaction(boardId, userId, type);
        return ResponseEntity.ok(Map.of("message", "반영되었습니다."));
    }

    // 추천/비추천 취소
    @Operation(summary = "게시글 추천/비추천 취소")
    @DeleteMapping("/{boardId}/reaction")
    public ResponseEntity<?> cancelReaction(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long boardId) {

        Long userId = getUserIdFromToken(token);
        boardService.deleteReaction(boardId, userId);
        return ResponseEntity.ok(Map.of("message", "취소되었습니다."));
    }

    // 댓글 목록 조회
    @Operation(summary = "댓글 목록 조회")
    @GetMapping("/{boardId}/comments")
    public ResponseEntity<?> getComments(
            @PathVariable Long boardId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false)
            String token) {

        Long userId = getUserIdIfExist(token);
        return ResponseEntity.ok(commentService.getComments(boardId, userId));
    }

    // 헬퍼 메서드
    private Long getUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtil.getUserId(token.substring(7));
        }
        throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
    }

    private Long getUserIdIfExist(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            try {
                return jwtUtil.getUserId(token.substring(7));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    // DTO 클래스
    @Data
    @Schema(description = "추천/비추천 요청 DTO")
    public static class ReactionRequestDto {
        @Schema(description = "반응 타입 (LIKE 또는 DISLIKE)", example = "LIKE")
        private String type;
    }
}