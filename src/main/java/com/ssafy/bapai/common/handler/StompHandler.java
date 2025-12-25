package com.ssafy.bapai.common.handler;

import com.ssafy.bapai.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil; // JWT 검증을 위한 유틸 추가
    private static final String REDIS_PREFIX = "group:connect:";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // 1. CONNECT 시점에 보안 검증 및 Redis 등록
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            String groupId = accessor.getFirstNativeHeader("groupId");

            // 토큰 검증 (Bearer 자르기 로직 포함)
            if (token != null && token.startsWith("Bearer ")) {
                String jwt = token.substring(7);
                if (jwtUtil.validateToken(jwt)) {
                    String userId = String.valueOf(jwtUtil.getUserId(jwt));

                    if (groupId != null) {
                        // 세션 속성에 저장 (DISCONNECT 시 사용)
                        accessor.getSessionAttributes().put("groupId", groupId);
                        accessor.getSessionAttributes().put("userId", userId);

                        // Redis Set에 현재 접속 중인 유저 추가
                        redisTemplate.opsForSet().add(REDIS_PREFIX + groupId, userId);
                        log.info("✅ STOMP 인증 성공 & 접속: 그룹 {}, 유저 {}", groupId, userId);
                    }
                } else {
                    log.error("❌ STOMP 인증 실패: 유효하지 않은 토큰");
                    throw new MessageDeliveryException("인증 오류");
                }
            } else {
                log.error("❌ STOMP 인증 실패: 헤더 누락");
                throw new MessageDeliveryException("인증 헤더가 없습니다.");
            }
        }

        // 2. DISCONNECT 시 Redis에서 제거
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