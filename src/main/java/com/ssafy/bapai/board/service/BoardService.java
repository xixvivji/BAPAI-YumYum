package com.ssafy.bapai.board.service;

import com.ssafy.bapai.board.dto.BoardDto;
import java.util.List;

public interface BoardService {
    List<BoardDto> getBoardList(String category);

    BoardDto getBoardDetail(Long boardId);

    void writeBoard(BoardDto boardDto);

    void modifyBoard(BoardDto boardDto);

    void removeBoard(Long boardId);
}