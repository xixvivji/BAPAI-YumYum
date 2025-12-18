package com.ssafy.bapai.common.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

// Redis에 "refreshToken:{userId}" 형태로 저장됨
// 1209600초 = 14일 (유효기간 지나면 자동 삭제)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "refreshToken", timeToLive = 1209600)
public class RefreshToken {

    @Id // javax.persistence.Id 아님! (Spring Data Redis용)
    private String userId;

    private String token;  // 리프레시 토큰 값

    @TimeToLive // 만료 시간 (선택 사항, 위 @RedisHash에 설정했으면 생략 가능)
    private Long expiration;
}