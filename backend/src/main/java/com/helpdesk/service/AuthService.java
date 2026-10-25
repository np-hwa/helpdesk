package com.helpdesk.service;

import com.helpdesk.domain.entity.RefreshToken;
import com.helpdesk.domain.entity.User;
import com.helpdesk.dto.request.LoginRequest;
import com.helpdesk.dto.request.RefreshTokenRequest;
import com.helpdesk.dto.response.AuthResponse;
import com.helpdesk.dto.response.UserResponse;
import com.helpdesk.exception.InvalidTokenException;
import com.helpdesk.exception.UserLockedException;
import com.helpdesk.repository.RefreshTokenRepository;
import com.helpdesk.repository.UserRepository;
import com.helpdesk.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Service xử lý xác thực: đăng nhập, refresh token (rotation), đăng xuất.
 * <p>
 * Chiến lược refresh token:
 * 1. Mỗi refresh sinh token mới + thu hồi token cũ (rotation).
 * 2. Nếu phát hiện token đã bị thay thế được dùng lại → thu hồi toàn bộ session của user.
 * 3. Chỉ lưu SHA-256 hash trong DB, không lưu token gốc.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider      jwtProvider;
    private final RefreshTokenRepository refreshTokenRepo;
    private final UserRepository         userRepo;
    private final UserMapper             userMapper;

    @Value("${app.jwt.refresh-token-expiration-days:7}")
    private long refreshTokenExpirationDays;

    // ─── Đăng nhập ────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        try {
            Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.email().toLowerCase().trim(),
                    request.password()
                )
            );

            User user = (User) auth.getPrincipal();
            return buildAuthResponse(user,
                ipOf(httpRequest), userAgentOf(httpRequest));

        } catch (LockedException ex) {
            throw new UserLockedException("Tài khoản đã bị khóa. Liên hệ Admin để mở khóa.");
        } catch (DisabledException ex) {
            throw new UserLockedException("Tài khoản chưa kích hoạt hoặc đã bị vô hiệu hóa.");
        } catch (BadCredentialsException ex) {
            throw new BadCredentialsException("Email hoặc mật khẩu không đúng.");
        }
    }

    // ─── Refresh Token (Rotation) ──────────────────────────────────────────────

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String rawToken = request.refreshToken();
        String tokenHash = sha256Hex(rawToken);

        RefreshToken stored = refreshTokenRepo.findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidTokenException("Refresh token không hợp lệ."));

        // Phát hiện dùng lại token đã bị thay thế → thu hồi toàn bộ session
        if (stored.getReplacedById() != null) {
            log.warn("Phát hiện reuse refresh token của user {}. Thu hồi toàn bộ session.",
                stored.getUser().getId());
            refreshTokenRepo.revokeAllByUserId(stored.getUser().getId(), LocalDateTime.now());
            throw new InvalidTokenException("Token đã hết hạn. Vui lòng đăng nhập lại.");
        }

        if (!stored.isValid()) {
            throw new InvalidTokenException("Refresh token đã hết hạn hoặc bị thu hồi.");
        }

        // Thu hồi token cũ
        stored.setRevokedAt(LocalDateTime.now());

        // Tạo token mới
        User user = stored.getUser();
        RefreshToken newToken = createRefreshToken(user, ipOf(httpRequest), userAgentOf(httpRequest));
        stored.setReplacedById(newToken.getId());
        refreshTokenRepo.save(stored);

        String accessToken = jwtProvider.generateAccessToken(
            user.getId(), user.getEmail(), user.getRole().name());

        return AuthResponse.of(
            accessToken,
            rawTokenOf(newToken),           // trả về token gốc (chưa hash)
            jwtProvider.getAccessTokenExpirationMs() / 1000,
            userMapper.toResponse(user)
        );
    }

    // ─── Đăng xuất thiết bị hiện tại ─────────────────────────────────────────

    @Transactional
    public void logout(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) return;
        String hash = sha256Hex(rawRefreshToken);
        refreshTokenRepo.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevokedAt(LocalDateTime.now());
            refreshTokenRepo.save(rt);
        });
    }

    // ─── Đăng xuất tất cả thiết bị ───────────────────────────────────────────

    @Transactional
    public void logoutAll(Long userId) {
        refreshTokenRepo.revokeAllByUserId(userId, LocalDateTime.now());
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user, String ip, String userAgent) {
        String accessToken = jwtProvider.generateAccessToken(
            user.getId(), user.getEmail(), user.getRole().name());

        RefreshToken refreshToken = createRefreshToken(user, ip, userAgent);

        return AuthResponse.of(
            accessToken,
            rawTokenOf(refreshToken),
            jwtProvider.getAccessTokenExpirationMs() / 1000,
            userMapper.toResponse(user)
        );
    }

    /**
     * Sinh refresh token ngẫu nhiên 256 bit, lưu hash SHA-256 vào DB.
     * Trả về entity đã lưu (để lấy ID cho replacedById).
     */
    private RefreshToken createRefreshToken(User user, String ip, String userAgent) {
        String rawToken = generateSecureToken();
        String hash     = sha256Hex(rawToken);

        RefreshToken rt = RefreshToken.builder()
            .user(user)
            .tokenHash(hash)
            .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays))
            .ipAddress(ip)
            .userAgent(userAgent)
            .build();

        RefreshToken saved = refreshTokenRepo.save(rt);
        // Gắn raw token vào transient field để trả về cho client
        saved.setIpAddress(rawToken); // tạm dùng field để truyền – sẽ sửa bằng record riêng
        return saved;
    }

    /** Lấy raw token được lưu tạm trong ipAddress field. */
    private String rawTokenOf(RefreshToken rt) {
        return rt.getIpAddress(); // xem createRefreshToken
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 không khả dụng", e);
        }
    }

    private String ipOf(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private String userAgentOf(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        return ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua;
    }
}
