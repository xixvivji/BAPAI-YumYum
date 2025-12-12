package com.ssafy.bapai.board.controller;

import com.ssafy.bapai.board.dto.BoardDto;
import com.ssafy.bapai.board.service.BoardService;
import com.ssafy.bapai.board.service.CommentService;
import com.ssafy.bapai.common.dto.PageResponse;
import com.ssafy.bapai.common.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
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

    private final BoardService boardService;
    private final CommentService commentService;
    private final JwtUtil jwtUtil;
    private final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/";

    // 1. 게시글 목록 조회
    @Operation(summary = "게시글 목록 조회", description = "페이징 및 검색(key: title/content/nickname, word: 검색어) 지원")
    @GetMapping
    public ResponseEntity<PageResponse<BoardDto>> getList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category,

            @Parameter(description = "검색 키워드 (title, content, nickname)", example = "title")
            @RequestParam(required = false) String key,
            @Parameter(description = "검색어", example = "맛집")
            @RequestParam(required = false) String word) {

        return ResponseEntity.ok(boardService.getBoardList(page, size, category, key, word));
    }

    // 2. 게시글 상세 조회
    @Operation(summary = "게시글 상세 조회", description = "게시글 ID로 상세 내용을 조회하고 조회수를 1 증가시킵니다.")
    @GetMapping("/{boardId}")
    public ResponseEntity<BoardDto> getDetail(
            @Parameter(description = "게시글 ID", example = "1")
            @PathVariable Long boardId) {
        return ResponseEntity.ok(boardService.getBoardDetail(boardId));
    }

    // =====================================================================
    // 3. 글 작성 (멀티모달: 폼 데이터)
    // =====================================================================
    @Operation(summary = "게시글 작성 (이미지 포함)", description = "게시글 정보와 이미지 파일을 전송합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> write(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @ModelAttribute BoardDto boardDto, // ★ 폼 데이터로 받기
            @Parameter(description = "업로드할 이미지 파일 (선택)")
            @RequestPart(value = "file", required = false) MultipartFile file) {

        Long userId = getUserIdFromToken(token);
        boardDto.setUserId(userId);

        if (file != null && !file.isEmpty()) {
            String imgUrl = uploadFile(file);
            boardDto.setImgUrl(imgUrl);
        }

        boardService.writeBoard(boardDto);
        return ResponseEntity.ok(Map.of("message", "게시글이 등록되었습니다."));
    }

    // =====================================================================
    // 4. 글 수정 (멀티모달: 폼 데이터 - RequestPart 대신 ModelAttribute로 통일)
    // =====================================================================
    @Operation(summary = "게시글 수정", description = "파일을 새로 보내면 이미지가 교체되고, 안 보내면 기존 이미지가 유지됩니다.")
    @PutMapping(value = "/{boardId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long boardId,
            @ModelAttribute BoardDto boardDto, // ★ 수정 시에도 폼 데이터 사용 권장
            @Parameter(description = "새로운 이미지 파일 (없으면 기존 유지)")
            @RequestPart(value = "file", required = false) MultipartFile file) {

        Long userId = getUserIdFromToken(token); // ★ 본인 확인용 ID 추출

        // 파일 교체 로직
        if (file != null && !file.isEmpty()) {
            String imgUrl = uploadFile(file);
            boardDto.setImgUrl(imgUrl);
        }

        boardDto.setBoardId(boardId);
        boardDto.setUserId(userId); // 서비스에서 본인 확인 함

        try {
            boardService.modifyBoard(boardDto);
            return ResponseEntity.ok(Map.of("message", "게시글이 수정되었습니다."));
        } catch (SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 5. 글 삭제
    @Operation(summary = "게시글 삭제", description = "게시글을 삭제합니다.")
    @DeleteMapping("/{boardId}")
    public ResponseEntity<?> delete(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long boardId) {

        Long userId = getUserIdFromToken(token); // ★ 삭제 시 본인 확인 필수

        try {
            boardService.removeBoard(boardId, userId);
            return ResponseEntity.ok(Map.of("message", "게시글이 삭제되었습니다."));
        } catch (SecurityException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 6. 추천/비추천 (Body로 받기)
    @Operation(summary = "게시글 추천/비추천 등록", description = "Body에 { \"type\": \"LIKE\" } 형태로 전송")
    @PostMapping("/{boardId}/reaction")
    public ResponseEntity<?> addReaction(
            @RequestHeader("Authorization") String token,
            @PathVariable Long boardId,
            @RequestBody ReactionRequestDto requestDto) {

        String type = requestDto.getType();

        if (!"LIKE".equals(type) && !"DISLIKE".equals(type)) {
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 type입니다."));
        }

        Long userId = getUserIdFromToken(token);
        try {
            boardService.addBoardReaction(boardId, userId, type);
            return ResponseEntity.ok(Map.of("message", "반영되었습니다."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 7. 추천/비추천 취소
    @Operation(summary = "게시글 추천/비추천 취소", description = "기존에 눌렀던 좋아요/싫어요를 취소합니다.")
    @DeleteMapping("/{boardId}/reaction")
    public ResponseEntity<?> cancelReaction(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long boardId) {

        Long userId = getUserIdFromToken(token);
        try {
            boardService.deleteReaction(boardId, userId);
            return ResponseEntity.ok(Map.of("message", "취소되었습니다."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ==========================================
    // 8. 댓글 목록 조회
    // ==========================================
    @Operation(summary = "댓글 목록 조회", description = "로그인한 경우, 본인이 누른 추천/비추천 여부(userLiked)가 포함됩니다.")
    @GetMapping("/{boardId}/comments")
    public ResponseEntity<?> getComments(
            @Parameter(description = "게시글 ID", example = "1")
            @PathVariable Long boardId,

            // ★ 수정 포인트: 토큰을 선택적(required=false)으로 받음
            @Parameter(hidden = true)
            @RequestHeader(value = "Authorization", required = false) String token) {

        Long userId = null;

        // 1. 토큰이 있다면 userId 추출
        if (token != null && token.startsWith("Bearer ")) {
            try {
                userId = jwtUtil.getUserId(token.substring(7));
            } catch (Exception e) {
                // 토큰이 만료되었거나 이상하면 -> 그냥 비회원(null)으로 처리하고 목록 보여줌
                userId = null;
            }
        }

        // 2. 서비스에 boardId와 userId(없으면 null)를 같이 넘김
        return ResponseEntity.ok(commentService.getComments(boardId, userId));
    }
    // ==========================================
    // [Helper] 메서드들
    // ==========================================

    private String uploadFile(MultipartFile file) {
        try {
            File directory = new File(UPLOAD_DIR);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String savedFilename = UUID.randomUUID() + "_" + originalFilename;
            Path filePath = Paths.get(UPLOAD_DIR + savedFilename);

            Files.write(filePath, file.getBytes());

            return "/images/" + savedFilename;
        } catch (IOException e) {
            throw new RuntimeException("이미지 저장 실패", e);
        }
    }

    private Long getUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtil.getUserId(token.substring(7));
        }
        throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
    }

    // DTO 클래스
    @Data
    public static class ReactionRequestDto {
        private String type; // "LIKE" or "DISLIKE"
    }

} // Class End (여기가 진짜 끝!)