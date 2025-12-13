package com.ssafy.bapai.board.service;

import com.ssafy.bapai.board.dto.BoardDto;
import com.ssafy.bapai.common.dto.PageResponse;

public interface BoardService {

    // 페이징 적용된 목록 조회
    PageResponse<BoardDto> getBoardList(int page, int size, String category, String key,
                                        String word, Long userId);

    BoardDto getBoardDetail(Long boardId, Long userId);


    void writeBoard(BoardDto boardDto);

    void modifyBoard(BoardDto boardDto);

    void removeBoard(Long boardId, Long userId);

    // 게시글 추천/비추천
    void addBoardReaction(Long boardId, Long userId, String type);

    // 게시글 추천/ 비추천 취소
    void deleteReaction(Long boardId, Long userId);
}