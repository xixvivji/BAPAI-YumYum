package com.ssafy.bapai.group.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class GroupBoardDto {
    // group_board 테이블 컬럼
    private Long boardId;       // PK (기존 gbId -> boardId)
    private Long groupId;
    private Long userId;
    private String type;        // NOTICE, FREE
    private String title;
    private String content;
    private LocalDateTime createdAt;

    // 화면 표시용
    private String writerNickname;
    private boolean isWriter;
}