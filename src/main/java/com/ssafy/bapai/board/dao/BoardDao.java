package com.ssafy.bapai.board.dao;

import com.ssafy.bapai.board.dto.BoardDto;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BoardDao {

    // 1. 목록 조회 (검색 + 페이징 + 카테고리)
    List<BoardDto> selectBoardList(Map<String, Object> params);

    // 2. 전체 개수 조회
    long selectBoardCount(Map<String, Object> params);

    // 3. 상세 조회
    BoardDto selectBoardDetail(@Param("boardId") Long boardId, @Param("userId") Long userId);

    BoardDto selectBoardDetail(Long boardId);

    // 4. CRUD
    void insertBoard(BoardDto boardDto);

    void updateBoard(BoardDto boardDto); // updated_at = NOW() 포함

    void deleteBoard(Long boardId);

    void updateHit(Long boardId);


    //게시글 삭제 시 같이 지워야 할 것들

    void deleteCommentsByBoardId(Long boardId);      // 해당 게시글의 댓글 전체 삭제

    void deleteAllReactionsByBoardId(Long boardId);  // 해당 게시글의 좋아요 기록 전체 삭제


    // 추천/비추천 등록 및 취소

    // 1. 내 반응 조회 (취소 전 확인용)
    String selectReactionType(@Param("boardId") Long boardId, @Param("userId") Long userId);

    // 2. 기록 남기기 (등록)
    void insertBoardReaction(@Param("boardId") Long boardId,
                             @Param("userId") Long userId,
                             @Param("type") String type);

    // 3. 기록 삭제하기 (취소)
    void deleteBoardReaction(@Param("boardId") Long boardId, @Param("userId") Long userId);

    // 4. 카운트 증가 (등록 시)
    void increaseBoardReactionCount(@Param("boardId") Long boardId, @Param("type") String type);

    // 5. 카운트 감소 (취소 시)
    void decreaseBoardReactionCount(@Param("boardId") Long boardId, @Param("type") String type);
}