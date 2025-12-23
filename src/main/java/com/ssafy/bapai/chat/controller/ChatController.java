package com.ssafy.bapai.chat.controller;

import com.ssafy.bapai.chat.dto.ChatMessageDto;
import com.ssafy.bapai.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatController {
    private final SimpMessageSendingOperations messagingTemplate;
    private final ChatService chatService;

    @MessageMapping("/chat/message")
    public void message(ChatMessageDto message) {
        chatService.saveMessage(message); // DB 저장
        // /sub/chat/room/{groupId}를 구독하는 모든 유저에게 메시지 전송
        messagingTemplate.convertAndSend("/sub/chat/room/" + message.getGroupId(), message);
    }
}