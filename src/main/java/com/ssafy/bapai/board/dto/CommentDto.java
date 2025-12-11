package com.ssafy.bapai.board.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CommentDto {
    private Long commentId;
    private Long boardId;
    private Long userId;
    private String nickname; // 작성자 이름
    private Long parentId;   // 부모 댓글 ID (대댓글일 경우)

    private String content;
    private int likeCount;
    private int dislikeCount;
    private String createdAt;

    // 대댓글 리스트 (계층 구조용)
    private List<CommentDto> children = new ArrayList<>();
}