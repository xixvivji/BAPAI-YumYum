package com.ssafy.bapai.board.service;

import com.ssafy.bapai.board.dao.BoardDao;
import com.ssafy.bapai.board.dto.BoardDto;
import com.ssafy.bapai.common.dto.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private final BoardDao boardDao;

    @Override
    public PageResponse<BoardDto> getBoardList(int page, int size, String category) {
        // 1. 페이징 계산
        int offset = (page - 1) * size;

        // 2. 리스트 조회
        List<BoardDto> list = boardDao.selectBoardList(category, size, offset);

        // 3. 전체 개수 조회
        long totalElements = boardDao.countBoardList(category);

        // 4. 전체 페이지 수 계산
        int totalPages = (int) Math.ceil((double) totalElements / size);

        // 5. 결과 반환
        return new PageResponse<>(list, page, size, totalPages, totalElements);
    }

    @Override
    public BoardDto getBoardDetail(Long boardId) {
        boardDao.updateHit(boardId);
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

    @Override
    @Transactional // 두 쿼리가 모두 성공해야 함
    public void addBoardReaction(Long boardId, Long userId, String type) {
        try {
            // 1. 중복 투표 방지 테이블에 INSERT
            boardDao.insertBoardReaction(boardId, userId, type);

            // 2. 게시글 테이블 카운트 증가 UPDATE
            boardDao.increaseBoardReactionCount(boardId, type);

        } catch (Exception e) {
            // 이미 투표한 경우 (Unique Key 위반)
            throw new IllegalStateException("이미 참여한 투표입니다.");
        }
    }
}