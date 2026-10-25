package com.helpdesk.repository;

import com.helpdesk.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);

    /** Thu hồi tất cả token của user (đăng xuất toàn thiết bị / phát hiện đánh cắp). */
    @Modifying
    @Query("""
           UPDATE RefreshToken rt
           SET rt.revokedAt = :now
           WHERE rt.user.id = :userId
             AND rt.revokedAt IS NULL
           """)
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Dọn token đã hết hạn hoặc bị thu hồi (chạy bằng scheduler). */
    @Modifying
    @Query("""
           DELETE FROM RefreshToken rt
           WHERE rt.expiresAt < :cutoff
              OR rt.revokedAt IS NOT NULL
           """)
    int deleteExpiredOrRevoked(@Param("cutoff") LocalDateTime cutoff);
}
