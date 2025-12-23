package com.ssafy.bapai.chat.dao;

import com.ssafy.bapai.chat.dto.ChatMessageDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatDao {
    // 메시지 저장
    void insertChatMessage(ChatMessageDto message);

    // 이전 채팅 내역 조회
    List<ChatMessageDto> selectChatHistory(Long groupId);
}