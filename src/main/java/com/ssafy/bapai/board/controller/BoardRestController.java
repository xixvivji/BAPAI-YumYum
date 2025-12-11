package com.ssafy.bapai.board.controller;

import com.ssafy.bapai.board.dto.BoardDto;
import com.ssafy.bapai.board.service.BoardService;
import com.ssafy.bapai.common.dto.PageResponse;
import com.ssafy.bapai.common.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/boards")
@RequiredArgsConstructor
@Tag(name = "3. 게시판 API", description = "자유/리뷰/전문가 게시판 CRUD 및 추천 기능")
public class BoardRestController {

    private final BoardService boardService;
    private final JwtUtil jwtUtil;

    // 1. 게시글 목록 조회
    @Operation(summary = "게시글 목록 조회", description = "카테고리별 조회. page(기본1), size(기본10) 페이징 지원.")
    @GetMapping
    public ResponseEntity<PageResponse<BoardDto>> getList(
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "카테고리 (FREE, REVIEW, EXPERT)", example = "FREE")
            @RequestParam(required = false) String category) {

        return ResponseEntity.ok(boardService.getBoardList(page, size, category));
    }

    // 2. 게시글 상세 조회
    @Operation(summary = "게시글 상세 조회", description = "게시글 ID로 상세 내용을 조회하고 조회수를 1 증가시킵니다.")
    @GetMapping("/{boardId}")
    public ResponseEntity<BoardDto> getDetail(
            @Parameter(description = "게시글 ID", example = "1")
            @PathVariable Long boardId) {
        return ResponseEntity.ok(boardService.getBoardDetail(boardId));
    }

    // 3. 글 작성
    @Operation(summary = "게시글 작성", description = "로그인한 사용자가 새로운 글을 작성합니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "작성할 게시글 정보",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BoardDto.class),
                    examples = @ExampleObject(
                            value = "{\n" +
                                    "  \"category\": \"FREE\",\n" +
                                    "  \"title\": \"다이어트 3일차 식단 공유합니다!\",\n" +
                                    "  \"content\": \"아침엔 샐러드, 점심엔 현미밥 먹었어요. 저녁 메뉴 추천 부탁드려요.\",\n" +
                                    "  \"imgUrl\": \"https://my-bucket.s3.ap-northeast-2.amazonaws.com/diet.jpg\"\n" +
                                    "}"
                    )
            )
    )
    @PostMapping
    public ResponseEntity<?> write(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @RequestBody BoardDto boardDto) {

        Long userId = getUserIdFromToken(token);
        boardDto.setUserId(userId);
        boardService.writeBoard(boardDto);
        return ResponseEntity.ok(Map.of("message", "게시글이 등록되었습니다."));
    }

    // 4. 글 수정
    @Operation(summary = "게시글 수정", description = "기존 게시글의 제목, 내용 등을 수정합니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "수정할 게시글 내용",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BoardDto.class),
                    examples = @ExampleObject(
                            value = "{\n" +
                                    "  \"category\": \"REVIEW\",\n" +
                                    "  \"title\": \"(수정) 식단 꿀팁 공유\",\n" +
                                    "  \"content\": \"내용을 좀 더 보강했습니다. 닭가슴살은 삶아서 드세요!\",\n" +
                                    "  \"imgUrl\": \"https://my-bucket.s3.ap-northeast-2.amazonaws.com/new_image.jpg\"\n" +
                                    "}"
                    )
            )
    )
    @PutMapping("/{boardId}")
    public ResponseEntity<?> update(
            @Parameter(description = "게시글 ID", required = true, example = "1")
            @PathVariable Long boardId,
            @RequestBody BoardDto boardDto) {

        boardDto.setBoardId(boardId);
        boardService.modifyBoard(boardDto);
        return ResponseEntity.ok(Map.of("message", "게시글이 수정되었습니다."));
    }

    // 5. 글 삭제
    @Operation(summary = "게시글 삭제", description = "게시글을 삭제합니다.")
    @DeleteMapping("/{boardId}")
    public ResponseEntity<?> delete(
            @Parameter(description = "삭제할 게시글 ID", required = true)
            @PathVariable Long boardId) {
        boardService.removeBoard(boardId);
        return ResponseEntity.ok(Map.of("message", "게시글이 삭제되었습니다."));
    }

    // 6. 추천/비추천 하기
    @Operation(summary = "게시글 추천/비추천", description = "게시글에 좋아요(LIKE) 또는 싫어요(DISLIKE)를 누릅니다.")
    @PostMapping("/{boardId}/reaction")
    public ResponseEntity<?> addReaction(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long boardId,
            @Parameter(description = "반응 유형 (LIKE 또는 DISLIKE)", example = "LIKE", required = true)
            @RequestParam String type) {

        Long userId = getUserIdFromToken(token);
        try {
            boardService.addBoardReaction(boardId, userId, type);
            return ResponseEntity.ok(Map.of("message", "반영되었습니다."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Helper Method
    private Long getUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtil.getUserId(token.substring(7));
        }
        throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
    }
}