package com.ssafy.bapai.board.service;

import com.ssafy.bapai.board.dao.CommentDao;
import com.ssafy.bapai.board.dto.CommentDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentDao commentDao;

    // 1. 댓글 목록 조회 (대댓글 계층 구조 조립)
    @Override
    public List<CommentDto> getComments(Long boardId) {
        // (1) DB에서 해당 게시글의 모든 댓글을 가져옴 (평면 리스트)
        List<CommentDto> allComments = commentDao.selectCommentsByBoardId(boardId);

        // (2) 결과로 반환할 최상위(부모) 댓글 리스트
        List<CommentDto> rootComments = new ArrayList<>();

        // (3) ID로 댓글을 빠르게 찾기 위해 Map에 담기
        Map<Long, CommentDto> commentMap = new HashMap<>();
        for (CommentDto c : allComments) {
            commentMap.put(c.getCommentId(), c);
        }

        // (4) 부모-자식 연결 로직 (핵심!)
        for (CommentDto c : allComments) {
            if (c.getParentId() == null) {
                // 부모가 없으면 -> 최상위 댓글 목록에 추가
                rootComments.add(c);
            } else {
                // 부모가 있으면 -> 부모를 찾아서 그 자식 리스트(children)에 추가
                CommentDto parent = commentMap.get(c.getParentId());
                if (parent != null) {
                    parent.getChildren().add(c);
                }
            }
        }

        // 계층 구조로 정리된 최상위 댓글 리스트만 반환하면, 그 안에 자식들이 다 들어있음
        return rootComments;
    }

    // 2. 댓글 작성
    @Override
    public void writeComment(CommentDto dto) {
        commentDao.insertComment(dto);
    }

    // 3. 댓글 삭제
    @Override
    public void deleteComment(Long commentId) {
        // 실제 서비스에서는 여기서 '작성자가 맞는지' 확인하는 로직이 필요함 (Security 등 활용)
        commentDao.deleteComment(commentId);
    }

    // 4. 댓글 추천/비추천 (트랜잭션 필수)
    @Override
    @Transactional // 두 개의 DB 작업이 모두 성공해야 함
    public void addReaction(Long commentId, Long userId, String type) {
        try {
            // (1) 중복 투표 방지 테이블에 기록 (이미 했으면 여기서 에러 발생)
            commentDao.insertCommentReaction(commentId, userId, type);

            // (2) 댓글 테이블의 like_count 또는 dislike_count 증가
            commentDao.increaseCommentReactionCount(commentId, type);

        } catch (Exception e) {
            // DB 제약조건(Unique Key) 위반 시 예외 처리
            throw new IllegalStateException("이미 참여한 투표입니다.");
        }
    }
}