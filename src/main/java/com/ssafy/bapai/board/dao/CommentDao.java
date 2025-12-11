package com.ssafy.bapai.board.dao;

import com.ssafy.bapai.board.dto.CommentDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentDao {
    List<CommentDto> selectCommentsByBoardId(Long boardId);

    void insertComment(CommentDto dto);

    void deleteComment(Long commentId);

    // 추천 관련
    void insertCommentReaction(Long commentId, Long userId, String type);

    void increaseCommentReactionCount(Long commentId, String type);
}