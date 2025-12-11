package com.ssafy.bapai.board.dto;

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

    private String createdAt;   // 작성일

    private int likeCount;    // 추천
    private int dislikeCount; // 비추천
}