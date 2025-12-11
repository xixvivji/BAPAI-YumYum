package com.ssafy.bapai.board.service;

import com.ssafy.bapai.board.dto.BoardDto;
import com.ssafy.bapai.common.dto.PageResponse;

public interface BoardService {

    // 페이징 적용된 목록 조회
    PageResponse<BoardDto> getBoardList(int page, int size, String category);

    BoardDto getBoardDetail(Long boardId);

    void writeBoard(BoardDto boardDto);

    void modifyBoard(BoardDto boardDto);

    void removeBoard(Long boardId);

    // 게시글 추천/비추천
    void addBoardReaction(Long boardId, Long userId, String type);
}