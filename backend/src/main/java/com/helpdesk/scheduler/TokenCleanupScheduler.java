package com.helpdesk.scheduler;

import com.helpdesk.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduler dọn dẹp refresh token hết hạn hoặc đã thu hồi.
 * Chạy mỗi đêm lúc 02:00.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepo;

    @Scheduled(cron = "0 0 2 * * *") // 02:00 mỗi ngày
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = refreshTokenRepo.deleteExpiredOrRevoked(LocalDateTime.now());
        if (deleted > 0) {
            log.info("TokenCleanup: đã xóa {} refresh token hết hạn/đã thu hồi.", deleted);
        }
    }
}
