package com.ssafy.bapai.board.service;

import com.ssafy.bapai.board.dto.CommentDto;
import java.util.List;

public interface CommentService {
    // 1. 댓글 목록 조회
    List<CommentDto> getComments(Long boardId, Long userId);

    // 2. 댓글 작성
    void writeComment(CommentDto dto);


    // 3. 삭제 (userId를 같이 받아서 본인 확인)
    void removeComment(Long commentId, Long userId);

    // 4. 댓글 추천/비추천
    void addReaction(Long commentId, Long userId, String type);

    void modifyComment(CommentDto dto);


    // 추천/비추천 취소
    void deleteReaction(Long commentId, Long userId);
}