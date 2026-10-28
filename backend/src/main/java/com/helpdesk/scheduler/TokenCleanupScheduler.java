package com.helpdesk.scheduler;

import com.helpdesk.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduler dọn refresh token đã hết hạn từ hơn 7 ngày. Chạy mỗi đêm lúc 02:00
 * (giờ VN).
 * <p>
 * Chỉ xóa theo expires_at, KHÔNG xóa token đã thu hồi: token cũ sau rotation
 * phải còn
 * trong DB đến khi hết hạn để AuthService phát hiện được việc dùng lại (reuse
 * detection).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupScheduler {

    private static final int RETENTION_DAYS = 7;

    private final RefreshTokenRepository refreshTokenRepo;

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = refreshTokenRepo.deleteExpired(LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (deleted > 0) {
            log.info("TokenCleanup: đã xóa {} refresh token hết hạn quá {} ngày.", deleted, RETENTION_DAYS);
        }
    }
}