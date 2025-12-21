package com.ssafy.bapai.board.controller;

import com.ssafy.bapai.board.dto.BoardDto;
import com.ssafy.bapai.board.service.BoardService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
@Tag(name = "3. 게시판 API", description = "자유/리뷰/전문가 게시판 CRUD 및 추천 기능")
public class BoardRestController {

    private final S3Service s3Service;
    private final BoardService boardService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "게시글 목록 조회", description = "조건에 맞는 게시글 목록을 페이징하여 반환합니다.")
    @GetMapping
    public ResponseEntity<PageResponse<BoardDto>> getList(
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "한 페이지당 게시글 수", example = "10")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "카테고리 필터 (없으면 전체 조회)", example = "FREE",
                    schema = @Schema(allowableValues = {"FREE", "REVIEW", "EXPERT"}))
            @RequestParam(required = false) String category,

            @Parameter(description = "검색 조건 (제목, 내용, 작성자)", example = "title",
                    schema = @Schema(allowableValues = {"title", "content", "nickname"}))
            @RequestParam(required = false) String key,

            @Parameter(description = "검색어 (key가 없을 경우 제목+내용 통합 검색)", example = "단백질")
            @RequestParam(required = false) String word,

            @Parameter(description = "정렬 기준", example = "latest",
                    schema = @Schema(allowableValues = {"latest", "views", "likes", "comments"}))
            @RequestParam(defaultValue = "latest") String sort,

            @Parameter(hidden = true)
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        Long userId = getUserIdIfExist(token);
        return ResponseEntity.ok(
                boardService.getBoardList(page, size, category, key, word, sort, userId));
    }

    @Operation(summary = "게시글 상세 조회")
    @GetMapping("/{boardId}")
    public ResponseEntity<BoardDto> getDetail(
            @PathVariable Long boardId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false)
            String token) {
        Long userId = getUserIdIfExist(token);
        return ResponseEntity.ok(boardService.getBoardDetail(boardId, userId));
    }

    @Operation(summary = "게시글 작성")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> write(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @ModelAttribute BoardDto boardDto,
            @Parameter(description = "이미지 파일") @RequestParam(value = "file", required = false)
            MultipartFile file) { // ★ file로 통일

        Long userId = jwtUtil.getUserId(token.substring(7));
        boardDto.setUserId(userId);
        try {
            if (file != null && !file.isEmpty()) {
                String imgUrl = s3Service.uploadFile(file, "board");
                boardDto.setImgUrl(imgUrl);
            }
            boardService.writeBoard(boardDto);
            return ResponseEntity.ok(Map.of("message", "게시글이 등록되었습니다."));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "이미지 업로드 실패"));
        }
    }

    @Operation(summary = "게시글 수정")
    @PutMapping(value = "/{boardId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long boardId,
            @ModelAttribute BoardDto boardDto,
            @Parameter(description = "수정할 이미지 파일") @RequestParam(value = "file", required = false)
            MultipartFile file) { // ★ file로 통일

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
            return ResponseEntity.internalServerError().body(Map.of("message", "이미지 업로드 실패"));
        }
    }

    @Operation(summary = "게시글 삭제")
    @DeleteMapping("/{boardId}")
    public ResponseEntity<?> delete(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long boardId) {
        Long userId = getUserIdFromToken(token);
        boardService.removeBoard(boardId, userId);
        return ResponseEntity.ok(Map.of("message", "게시글이 삭제되었습니다."));
    }

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

    @Operation(summary = "게시글 추천/비추천 취소")
    @DeleteMapping("/{boardId}/reaction")
    public ResponseEntity<?> cancelReaction(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long boardId) {
        Long userId = getUserIdFromToken(token);
        boardService.deleteReaction(boardId, userId);
        return ResponseEntity.ok(Map.of("message", "취소되었습니다."));
    }

    private Long getUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtil.getUserId(token.substring(7));
        }
        throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
    }

    private Long getUserIdIfExist(String token) {
        try {
            return (token != null && token.startsWith("Bearer ")) ?
                    jwtUtil.getUserId(token.substring(7)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Data
    public static class ReactionRequestDto {
        private String type;
    }
}