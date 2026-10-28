package com.helpdesk.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bảng `refresh_tokens` (V1) – lưu trữ refresh token để phục vụ JWT rotation.
 * <p>
 * Thiết kế bảo mật:
 * - Chỉ lưu SHA-256 hash (64 ký tự hex) của token, KHÔNG lưu token gốc.
 * - Khi refresh: token cũ bị thu hồi (revoked_at ≠ null) và được trỏ tới token
 * mới
 * qua {@code replacedById} để phát hiện token đã bị đánh cắp (reuse detection).
 * - user_agent + ip_address: dấu vết thiết bị (forensics).
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_rt_user", columnList = "user_id, revoked_at"),
        @Index(name = "idx_rt_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rt_user"))
    private User user;

    /**
     * SHA-256 của token gốc (64 ký tự hex).
     * CHAR vì độ dài cố định; UNIQUE để không trùng và tra nhanh.
     */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** NULL = còn hiệu lực; có giá trị = đã đăng xuất / thu hồi. */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * Khi rotation: token cũ trỏ sang ID của token mới.
     * Nếu token cũ (đã replaced) được dùng lại → dấu hiệu đánh cắp → thu hồi toàn
     * bộ session.
     */
    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** IPv4 hoặc IPv6 (tối đa 45 ký tự). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isRevoked() && !isExpired();
    }
}
