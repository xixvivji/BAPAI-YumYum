package com.ssafy.bapai.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
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
    private String userLiked;
    // 작성일 (패턴: 년-월-일 시:분:초)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // 수정일 추가
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // 대댓글 리스트
    private List<CommentDto> children = new ArrayList<>();
}