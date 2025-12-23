package com.ssafy.bapai.chat.service;

import com.ssafy.bapai.chat.dao.ChatDao;
import com.ssafy.bapai.chat.dto.ChatMessageDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final ChatDao chatDao; // ChatDao 인터페이스 필요

    @Override
    public void saveMessage(ChatMessageDto message) {
        chatDao.insertChatMessage(message);
    }

    @Override
    public List<ChatMessageDto> getChatHistory(Long groupId) {
        return chatDao.selectChatHistory(groupId);
    }
}