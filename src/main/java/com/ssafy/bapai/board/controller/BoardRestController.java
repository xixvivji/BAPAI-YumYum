package com.ssafy.bapai.board.controller;

import com.ssafy.bapai.board.dto.BoardDto;
import com.ssafy.bapai.board.service.BoardService;
import com.ssafy.bapai.common.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
@Tag(name = "3. 게시판 API", description = "자유/리뷰/전문가 게시판 CRUD")
public class BoardRestController {

    private final BoardService boardService;
    private final JwtUtil jwtUtil;

    // 1. 게시글 목록 조회 (카테고리 선택 가능)
    // 요청 예시: /api/boards?category=FREE
    @Operation(summary = "게시글 목록 조회", description = "카테고리(FREE, REVIEW, EXPERT)별로 조회합니다. 없으면 전체 조회.")
    @GetMapping
    public ResponseEntity<List<BoardDto>> getList(@RequestParam(required = false) String category) {
        return ResponseEntity.ok(boardService.getBoardList(category));
    }

    // 2. 게시글 상세 조회
    @Operation(summary = "게시글 상세 조회", description = "게시글을 읽고 조회수를 1 올립니다.")
    @GetMapping("/{boardId}")
    public ResponseEntity<BoardDto> getDetail(@PathVariable Long boardId) {
        return ResponseEntity.ok(boardService.getBoardDetail(boardId));
    }

    // 3. 글 작성
    @Operation(summary = "게시글 작성", description = "로그인한 유저가 글을 작성합니다.")
    @PostMapping
    public ResponseEntity<?> write(@RequestHeader("Authorization") String token,
                                   @RequestBody BoardDto boardDto) {
        Long userId = jwtUtil.getUserId(token);
        boardDto.setUserId(userId); // 토큰에서 꺼낸 ID 세팅

        boardService.writeBoard(boardDto);
        return ResponseEntity.ok(Map.of("message", "게시글이 등록되었습니다."));
    }

    // 4. 글 수정
    @Operation(summary = "게시글 수정", description = "제목, 내용, 카테고리 등을 수정합니다.")
    @PutMapping("/{boardId}")
    public ResponseEntity<?> update(@PathVariable Long boardId,
                                    @RequestBody BoardDto boardDto) {
        boardDto.setBoardId(boardId);
        boardService.modifyBoard(boardDto);
        return ResponseEntity.ok(Map.of("message", "게시글이 수정되었습니다."));
    }

    // 5. 글 삭제
    @Operation(summary = "게시글 삭제", description = "게시글을 삭제합니다.")
    @DeleteMapping("/{boardId}")
    public ResponseEntity<?> delete(@PathVariable Long boardId) {
        boardService.removeBoard(boardId);
        return ResponseEntity.ok(Map.of("message", "게시글이 삭제되었습니다."));
    }
}