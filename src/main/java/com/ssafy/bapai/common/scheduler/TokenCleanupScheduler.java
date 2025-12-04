package com.ssafy.bapai.common.scheduler;

import com.ssafy.bapai.member.dao.RefreshTokenDao;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenDao refreshTokenDao;

    // 매일 새벽 4시에 실행
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupExpiredTokens() {

        int count = refreshTokenDao.deleteExpiredTokens();
        System.out.println("만료된 토큰 청소 완료! 삭제된 개수: " + count);
    }
}