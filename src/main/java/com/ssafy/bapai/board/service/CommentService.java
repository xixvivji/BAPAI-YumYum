package com.ssafy.bapai.board.service;

import com.ssafy.bapai.board.dto.CommentDto;
import java.util.List;

public interface CommentService {
    // 1. 댓글 목록 조회 (계층 구조로 변환해서 반환)
    List<CommentDto> getComments(Long boardId);

    // 2. 댓글 작성
    void writeComment(CommentDto dto);

    // 3. 댓글 삭제
    void deleteComment(Long commentId);

    // 4. 댓글 추천/비추천
    void addReaction(Long commentId, Long userId, String type);
}