package com.ssafy.bapai.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BoardDto {
    private Long boardId;       // PK
    private Long userId;        // 작성자 ID (FK)
    private String nickname;    // 작성자 닉네임 (조인해서 가져옴)

    private String category;    // 카테고리 (FREE, REVIEW, EXPERT)
    private String title;       // 제목
    private String content;     // 내용
    private String imgUrl;      // 이미지 주소
    private int viewCount;      // 조회수

    // 작성일 (패턴: 년-월-일 시:분:초)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // 수정일 추가
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    private int likeCount;    // 추천
    private int dislikeCount; // 비추천
    private String userLiked;
}