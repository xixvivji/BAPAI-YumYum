package com.ssafy.bapai.board.dao;

import com.ssafy.bapai.board.dto.BoardDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BoardDao {
    // category가 null이면 전체 조회, 있으면 해당 카테고리만 조회
    List<BoardDto> selectBoardList(@Param("category") String category,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    // 2. 전체 개수 조회 (페이징 계산용)
    long countBoardList(String category);

    BoardDto selectBoardById(Long boardId);

    void insertBoard(BoardDto boardDto);

    void updateBoard(BoardDto boardDto);

    void deleteBoard(Long boardId);

    void updateHit(Long boardId);

    // [추천/비추천]
    // 1. 기록 남기기 (중복 방지)
    void insertBoardReaction(@Param("boardId") Long boardId,
                             @Param("userId") Long userId,
                             @Param("type") String type);

    // 2. 카운트 증가
    void increaseBoardReactionCount(@Param("boardId") Long boardId,
                                    @Param("type") String type);
}