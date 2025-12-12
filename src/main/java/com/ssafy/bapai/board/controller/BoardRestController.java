package com.ssafy.bapai.board.controller;

import com.ssafy.bapai.board.dto.BoardDto;
import com.ssafy.bapai.board.service.BoardService;
import com.ssafy.bapai.board.service.CommentService;
import com.ssafy.bapai.common.dto.PageResponse;
import com.ssafy.bapai.common.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "카테고리 필터 (FREE, REVIEW, EXPERT)", example = "FREE")
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
            @Parameter(description = "조회할 게시글 ID", example = "1")
            @PathVariable Long boardId) {
        return ResponseEntity.ok(boardService.getBoardDetail(boardId));
    }

    // =====================================================================
    // 3. 글 작성 (멀티모달: 폼 데이터)
    // =====================================================================
    @Operation(summary = "게시글 작성", description = "제목, 내용, 카테고리, 이미지를 전송합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> write(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,

            // 1. @ModelAttribute 사용 (category, title, content를 알아서 받음)
            // ★ 프론트엔드에서 board라는 덩어리 없이 그냥 보내면 됩니다.
            @ModelAttribute BoardDto boardDto,

            // 2. 파일 이름 맞추기 (프론트가 'image'로 보낸다면 여기서도 'image'여야 함!)
            // ★ 만약 프론트가 'file'로 보낸다면 @RequestPart("file")로 유지하세요.
            @Parameter(description = "업로드할 이미지 파일")
            @RequestPart(value = "image", required = false) MultipartFile file) {

        // --- [디버깅 로그] ---
        System.out.println("1. 제목: " + boardDto.getTitle());
        System.out.println("2. 내용: " + boardDto.getContent());
        System.out.println("3. 파일: " + file);
        // -------------------

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
    // 4. 글 수정 (멀티모달: 폼 데이터)
    // =====================================================================
    @Operation(summary = "게시글 수정", description = "수정할 내용(제목, 내용, 카테고리)과 새 이미지 파일을 전송합니다.")
    @PutMapping(value = "/{boardId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,

            @Parameter(description = "수정할 게시글 ID", example = "1")
            @PathVariable Long boardId,

            // 1. @ModelAttribute 사용 (title, content, category 자동 매핑)
            // ★ 작성(write) 때와 똑같이 맞춤!
            @ModelAttribute BoardDto boardDto,

            // 2. 파일 받기 (프론트가 'image'로 보내므로 여기서도 'image')
            @Parameter(description = "새로운 이미지 파일 (선택사항: 안 보내면 기존 이미지 유지)")
            @RequestPart(value = "image", required = false) MultipartFile file) {

        // --- [디버깅 로그] ---
        System.out.println("=== UPDATE 요청 도착 ===");
        System.out.println("1. 수정할 ID: " + boardId);
        System.out.println("2. 제목: " + boardDto.getTitle());
        System.out.println("3. 내용: " + boardDto.getContent());
        System.out.println("4. 파일: " + file);
        // -------------------

        Long userId = getUserIdFromToken(token);

        // 파일 교체 로직
        if (file != null && !file.isEmpty()) {
            String imgUrl = uploadFile(file);
            System.out.println("5. 새 이미지 URL: " + imgUrl);
            boardDto.setImgUrl(imgUrl);
        }

        boardDto.setBoardId(boardId);
        boardDto.setUserId(userId);

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

    // 6. 추천/비추천 (Body로 받기)
    @Operation(summary = "게시글 추천/비추천 등록",
            description = "Body에 JSON 형태로 타입을 전송합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReactionRequestDto.class),
                            examples = @ExampleObject(value = "{\"type\": \"LIKE\"}") // ★ 여기가 핵심! 미리보기 제공
                    )
            ))
    @PostMapping("/{boardId}/reaction")
    public ResponseEntity<?> addReaction(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @Parameter(description = "게시글 ID", example = "1") @PathVariable Long boardId,
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
            @Parameter(description = "게시글 ID", example = "1") @PathVariable Long boardId) {

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

            @Parameter(hidden = true)
            @RequestHeader(value = "Authorization", required = false) String token) {

        Long userId = null;

        if (token != null && token.startsWith("Bearer ")) {
            try {
                userId = jwtUtil.getUserId(token.substring(7));
            } catch (Exception e) {
                userId = null;
            }
        }

        return ResponseEntity.ok(commentService.getComments(boardId, userId));
    }

    // ==========================================
    // [Helper] 메서드들
    // ==========================================
    // ... (uploadFile, getUserIdFromToken 등 기존 로직 동일) ...
    private String uploadFile(MultipartFile file) {
        try {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
            }
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                throw new IllegalArgumentException("파일명이 없습니다.");
            }
            String lowerName = originalFilename.toLowerCase();
            if (!(lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                    lowerName.endsWith(".png"))) {
                throw new IllegalArgumentException("jpg, jpeg, png 파일만 업로드할 수 있습니다.");
            }
            File directory = new File(UPLOAD_DIR);
            if (!directory.exists()) {
                directory.mkdirs();
            }
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
    @Schema(description = "추천/비추천 요청 DTO")
    public static class ReactionRequestDto {

        @Schema(description = "반응 타입 (LIKE 또는 DISLIKE)", example = "LIKE")
        private String type;
    }

}