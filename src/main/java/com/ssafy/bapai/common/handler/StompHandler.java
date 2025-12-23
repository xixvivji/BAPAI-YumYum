package com.ssafy.bapai.common.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private final StringRedisTemplate redisTemplate;
    private static final String REDIS_PREFIX = "group:connect:";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // 1. 연결 시 Redis에 유저 추가
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String groupId = accessor.getFirstNativeHeader("groupId");
            String userId = accessor.getFirstNativeHeader("userId");

            if (groupId != null && userId != null) {
                // 세션 스토리지에 정보 임시 저장 (연결 끊길 때 사용)
                accessor.getSessionAttributes().put("groupId", groupId);
                accessor.getSessionAttributes().put("userId", userId);

                redisTemplate.opsForSet().add(REDIS_PREFIX + groupId, userId);
                log.info("✅ 유저 접속: 그룹 {}, 유저 {}", groupId, userId);
            }
        }
        // 2. 연결 해제 시 Redis에서 제거
        else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            String groupId = (String) accessor.getSessionAttributes().get("groupId");
            String userId = (String) accessor.getSessionAttributes().get("userId");

            if (groupId != null && userId != null) {
                redisTemplate.opsForSet().remove(REDIS_PREFIX + groupId, userId);
                log.info("❌ 유저 이탈: 그룹 {}, 유저 {}", groupId, userId);
            }
        }
        return message;
    }
}