package com.ssafy.bapai.chat.service;

import com.ssafy.bapai.chat.dto.ChatMessageDto;
import java.util.List;

public interface ChatService {
    void saveMessage(ChatMessageDto message);

    List<ChatMessageDto> getChatHistory(Long groupId);
}