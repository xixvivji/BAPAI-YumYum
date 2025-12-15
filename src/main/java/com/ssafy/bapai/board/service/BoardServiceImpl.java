package com.ssafy.bapai.board.service;

import com.ssafy.bapai.board.dao.BoardDao;
import com.ssafy.bapai.board.dto.BoardDto;
import com.ssafy.bapai.common.dto.PageResponse;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private final BoardDao boardDao;
    private final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/";

    @Override
    public PageResponse<BoardDto> getBoardList(int page, int size, String category, String key,
                                               String word, Long userId) {
        int offset = (page - 1) * size;

        Map<String, Object> params = new HashMap<>();
        params.put("size", size);
        params.put("offset", offset);
        params.put("category", category);
        params.put("key", key);
        params.put("word", word);

        // 맵에 userId 담기 XML에서 쓸 거
        params.put("userId", userId);

        List<BoardDto> list = boardDao.selectBoardList(params);
        long totalElements = boardDao.selectBoardCount(params);

        return new PageResponse<>(list, page, size, totalElements);
    }

    @Override
    public BoardDto getBoardDetail(Long boardId, Long userId) {
        boardDao.updateHit(boardId);
        // userId도 같이 넘김
        return boardDao.selectBoardDetail(boardId, userId);
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
    @Transactional
    public void removeBoard(Long boardId, Long userId) {
        // 1. 본인 확인 및 게시글 정보 조회
        BoardDto board = boardDao.selectBoardDetail(boardId);
        if (board == null) {
            throw new IllegalArgumentException("게시글이 없습니다.");
        }
        if (!board.getUserId().equals(userId)) {
            throw new SecurityException("권한이 없습니다.");
        }

        // 2. 댓글 삭제 (해당 게시글의 모든 댓글)

        boardDao.deleteCommentsByBoardId(boardId);

        // 3. 게시글 좋아요/싫어요 기록 삭제
        boardDao.deleteAllReactionsByBoardId(boardId);

        // 4. 첨부 파일 삭제
        if (board.getImgUrl() != null && !board.getImgUrl().isEmpty()) {
            // /images/uuid_filename.jpg -> uuid_filename.jpg 추출
            String fileName = board.getImgUrl().replace("/images/", "");
            File file = new File(UPLOAD_DIR + fileName);
            if (file.exists()) {
                file.delete(); // 실제 파일 삭제
            }
        }

        //게시글 삭제
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

    @Override
    @Transactional
    public void deleteReaction(Long boardId, Long userId) {
        // 1. 내 반응 조회
        String type = boardDao.selectReactionType(boardId, userId);
        if (type == null) {
            throw new IllegalStateException("취소할 반응이 없습니다.");
        }

        // 2. 삭제
        boardDao.deleteBoardReaction(boardId, userId);

        // 3. 카운트 감소
        boardDao.decreaseBoardReactionCount(boardId, type);
    }
}