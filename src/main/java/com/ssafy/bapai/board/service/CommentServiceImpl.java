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

    // 1. 댓글 목록 조회
    @Override
    public List<CommentDto> getComments(Long boardId, Long userId) {
        // DB에서 해당 게시글의 모든 댓글을 가져옴
        List<CommentDto> allComments = commentDao.selectCommentsByBoardId(boardId, userId);

        // 결과로 반환할 최상위(부모) 댓글 리스트
        List<CommentDto> rootComments = new ArrayList<>();

        // ID로 댓글을 빠르게 찾기 위해 Map에 담기
        Map<Long, CommentDto> commentMap = new HashMap<>();
        for (CommentDto c : allComments) {
            commentMap.put(c.getCommentId(), c);
        }

        for (CommentDto c : allComments) {
            if (c.getParentId() == null) {
                // 부모가 없으면 -> 최상위 댓글 목록에 추가
                rootComments.add(c);
            } else {
                // 부모가 있으면 -> 부모를 찾아서 그 자식에 추가
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


    // 3. 댓글 수정 (본인 확인 추가)
    @Override
    public void modifyComment(CommentDto dto) {
        checkOwner(dto.getCommentId(), dto.getUserId()); // 본인 확인
        commentDao.updateComment(dto);
    }

    // 4. 댓글 삭제 (본인 확인 추가)
    @Override
    public void removeComment(Long commentId, Long userId) {
        checkOwner(commentId, userId); // 본인 확인
        commentDao.deleteComment(commentId);
    }

    // 5. 댓글 추천/비추천 등록
    @Override
    @Transactional
    public void addReaction(Long commentId, Long userId, String type) {
        try {
            commentDao.insertCommentReaction(commentId, userId, type);
            commentDao.increaseCommentReactionCount(commentId, type);
        } catch (Exception e) {
            throw new IllegalStateException("이미 반응을 남겼습니다.");
        }
    }

    // 6. 댓글 추천/비추천 취소 (조회 -> 삭제 -> 감소)
    @Override
    @Transactional
    public void deleteReaction(Long commentId, Long userId) {
        // (1) 내가 무슨 반응을 했는지 확인
        String type = commentDao.selectReactionType(commentId, userId);
        if (type == null) {
            throw new IllegalStateException("취소할 반응이 없습니다.");
        }

        // (2) 반응 테이블에서 삭제
        int result = commentDao.deleteCommentReaction(commentId, userId);
        if (result == 0) {
            throw new IllegalStateException("이미 삭제되었거나 존재하지 않습니다.");
        }

        // (3) 집계 테이블에서 카운트 감소
        commentDao.decreaseCommentReactionCount(commentId, type);
    }

    // 본인 확인 메서드
    private void checkOwner(Long commentId, Long userId) {
        CommentDto comment = commentDao.selectCommentById(commentId);
        if (comment == null) {
            throw new IllegalArgumentException("존재하지 않는 댓글입니다.");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new SecurityException("권한이 없습니다."); // 또는 AccessDeniedException
        }
    }
}