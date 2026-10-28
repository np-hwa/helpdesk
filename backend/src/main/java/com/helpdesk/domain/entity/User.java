package com.helpdesk.domain.entity;

import com.helpdesk.domain.enums.Role;
import com.helpdesk.domain.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Bảng `users` – người dùng hệ thống.
 * <p>
 * Implement {@link UserDetails} để Spring Security dùng trực tiếp.
 * 4 vai trò: ADMIN | IT_LEADER | IT_SUPPORT | EMPLOYEE.
 * Mật khẩu lưu hash BCrypt (60 ký tự), không bao giờ lưu plaintext.
 * email_notify (V1): bật/tắt nhận thông báo qua Gmail.
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"), indexes = @Index(name = "idx_users_role_status", columnList = "role, status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String email;

    /** BCrypt hash – tuyệt đối không lưu plaintext. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", foreignKey = @ForeignKey(name = "fk_users_department"))
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** V4: bật/tắt nhận thông báo qua Gmail. */
    @Column(name = "email_notify", nullable = false)
    @Builder.Default
    private Boolean emailNotify = true;

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null)
            createdAt = now;
        if (updatedAt == null)
            updatedAt = now;
        if (status == null)
            status = UserStatus.ACTIVE;
        if (emailNotify == null)
            emailNotify = true;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ─── Spring Security: UserDetails ──────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Spring Security dùng trường này làm "password" để so sánh. */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Spring Security dùng email làm username. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    /** Mã ticket hiển thị, ví dụ TCK-000123. */
    public static String formatTicketCode(Long id) {
        return "TCK-%06d".formatted(id);
    }
}
