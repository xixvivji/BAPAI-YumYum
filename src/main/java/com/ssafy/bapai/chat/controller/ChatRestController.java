package com.ssafy.bapai.chat.controller;

import com.ssafy.bapai.chat.dto.ChatMessageDto;
import com.ssafy.bapai.chat.service.ChatService;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;
    private final StringRedisTemplate redisTemplate;

    // 대화 내역 조회
    @GetMapping("/{groupId}/history")
    public ResponseEntity<List<ChatMessageDto>> getHistory(@PathVariable Long groupId) {
        return ResponseEntity.ok(chatService.getChatHistory(groupId));
    }

    // 아까 만든 실시간 접속자 조회 (녹색 불)
    @GetMapping("/{groupId}/online")
    public ResponseEntity<Set<String>> getOnlineUsers(@PathVariable Long groupId) {
        Set<String> onlineUsers = redisTemplate.opsForSet().members("group:connect:" + groupId);
        return ResponseEntity.ok(onlineUsers != null ? onlineUsers : Collections.emptySet());
    }
}