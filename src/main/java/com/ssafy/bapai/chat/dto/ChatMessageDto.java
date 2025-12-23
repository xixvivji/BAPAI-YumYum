package com.ssafy.bapai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private Long chatId;
    private Long groupId;
    private Long userId;
    private String senderName; // 닉네임 표시용
    private String message;
    private String createdAt;
}