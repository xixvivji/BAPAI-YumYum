package com.ssafy.bapai.board.dao;

import com.ssafy.bapai.board.dto.BoardDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BoardDao {
    // category가 null이면 전체 조회, 있으면 해당 카테고리만 조회
    List<BoardDto> selectAllBoards(String category);

    BoardDto selectBoardById(Long boardId);

    void insertBoard(BoardDto boardDto);

    void updateBoard(BoardDto boardDto);

    void deleteBoard(Long boardId);

    void updateHit(Long boardId);
}