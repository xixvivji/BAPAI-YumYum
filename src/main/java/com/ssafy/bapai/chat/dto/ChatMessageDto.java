package com.ssafy.bapai.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
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

    @JsonProperty("senderId")
    private Long userId;

    @JsonProperty("senderNickname")
    private String senderName;

    @JsonProperty("content")
    private String message;

    @JsonProperty("timestamp")
    private LocalDateTime createdAt;
}