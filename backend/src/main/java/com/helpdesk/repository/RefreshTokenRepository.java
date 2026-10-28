package com.helpdesk.repository;

import com.helpdesk.domain.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

       /**
        * Dùng cho refresh: khóa dòng (SELECT ... FOR UPDATE) để hai request đồng thời
        * với cùng một token không cùng vượt qua kiểm tra. Phải gọi
        * trong @Transactional.
        */
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :hash")
       Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

       List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);

       /**
        * Thu hồi tất cả token của user (đăng xuất toàn thiết bị / phát hiện đánh cắp).
        */
       @Modifying
       @Query("""
                     UPDATE RefreshToken rt
                     SET rt.revokedAt = :now
                     WHERE rt.user.id = :userId
                       AND rt.revokedAt IS NULL
                     """)
       int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

       /**
        * Dọn token đã hết hạn quá lâu (scheduler gọi với cutoff = now - 7 ngày).
        * KHÔNG xóa theo revokedAt: token đã thu hồi phải còn trong DB đến khi hết hạn
        * để phát hiện reuse. Nơi gọi phải có @Transactional.
        */
       @Modifying
       @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoff")
       int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}