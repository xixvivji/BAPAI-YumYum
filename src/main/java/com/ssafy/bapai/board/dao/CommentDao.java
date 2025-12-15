package com.ssafy.bapai.board.dao;

import com.ssafy.bapai.board.dto.CommentDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentDao {
    List<CommentDto> selectCommentsByBoardId(@Param("boardId") Long boardId,
                                             @Param("userId") Long userId);

    CommentDto selectCommentById(Long commentId);

    void insertComment(CommentDto dto);

    void updateComment(CommentDto dto);

    void deleteComment(Long commentId);

    // 추천 관련

    // 1. 반응 등록
    void insertCommentReaction(@Param("commentId") Long commentId, @Param("userId") Long userId,
                               @Param("type") String type);

    // 2. 반응 카운트 증가
    void increaseCommentReactionCount(@Param("commentId") Long commentId,
                                      @Param("type") String type);

    // 3. 내가 어떤 반응을 했는지 조회 (취소할 때 필요)
    String selectReactionType(@Param("commentId") Long commentId, @Param("userId") Long userId);

    // 4. 반응 기록 삭제 (취소)
    int deleteCommentReaction(@Param("commentId") Long commentId, @Param("userId") Long userId);

    // 5. 반응 카운트 감소 (취소 시)
    void decreaseCommentReactionCount(@Param("commentId") Long commentId,
                                      @Param("type") String type);
}