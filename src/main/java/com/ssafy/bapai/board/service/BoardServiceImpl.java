package com.ssafy.bapai.board.service;

import com.ssafy.bapai.board.dao.BoardDao;
import com.ssafy.bapai.board.dto.BoardDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private final BoardDao boardDao;

    @Override
    public List<BoardDto> getBoardList(String category) {
        return boardDao.selectAllBoards(category);
    }

    @Override
    public BoardDto getBoardDetail(Long boardId) {
        boardDao.updateHit(boardId); // 조회수 증가
        return boardDao.selectBoardById(boardId);
    }

    @Override
    public void writeBoard(BoardDto boardDto) {
        boardDao.insertBoard(boardDto);
    }

    @Override
    public void modifyBoard(BoardDto boardDto) {
        boardDao.updateBoard(boardDto);
    }

    @Override
    public void removeBoard(Long boardId) {
        boardDao.deleteBoard(boardId);
    }
}