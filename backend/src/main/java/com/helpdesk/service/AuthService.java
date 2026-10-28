package com.helpdesk.service;

import com.helpdesk.domain.entity.RefreshToken;
import com.helpdesk.domain.entity.User;
import com.helpdesk.dto.request.LoginRequest;
import com.helpdesk.dto.request.RefreshTokenRequest;
import com.helpdesk.dto.response.AuthResponse;
import com.helpdesk.exception.InvalidTokenException;
import com.helpdesk.exception.UserLockedException;
import com.helpdesk.repository.RefreshTokenRepository;
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
 * 2. Nếu token đã bị thay thế được dùng lại → thu hồi toàn bộ session của user.
 * 3. Chỉ lưu SHA-256 hash trong DB; token gốc chỉ tồn tại trong bộ nhớ và
 * response.
 * 4. Refresh luôn kiểm tra lại trạng thái user (LOCKED/INACTIVE bị chặn).
 * <p>
 * YÊU CẦU ở RefreshTokenRepository (khóa dòng để chống hai request refresh đồng
 * thời):
 * 
 * <pre>
 * &#64;Lock(LockModeType.PESSIMISTIC_WRITE)
 * &#64;Query("select rt from RefreshToken rt where rt.tokenHash = :hash")
 * Optional&lt;RefreshToken&gt; findByTokenHashForUpdate(&#64;Param("hash") String hash);
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_IP_LENGTH = 45; // VARCHAR(45) – đủ cho IPv6
    private static final int MAX_USER_AGENT_LENGTH = 255; // VARCHAR(255)

    private final AuthenticationManager authManager;
    private final JwtTokenProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepo;
    private final UserMapper userMapper;

    @Value("${app.jwt.refresh-token-expiration-days:7}")
    private long refreshTokenExpirationDays;

    /**
     * Refresh token vừa phát hành: entity đã lưu (chỉ có hash) + token gốc để trả
     * cho client.
     */
    private record IssuedToken(RefreshToken entity, String rawToken) {
    }

    // ─── Đăng nhập ────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email().trim().toLowerCase(),
                            request.password()));

            User user = (User) auth.getPrincipal();
            return buildAuthResponse(user, ipOf(httpRequest), userAgentOf(httpRequest));

        } catch (LockedException ex) {
            throw new UserLockedException("Tài khoản đã bị khóa. Liên hệ Admin để mở khóa.");
        } catch (DisabledException ex) {
            throw new UserLockedException("Tài khoản chưa kích hoạt hoặc đã bị vô hiệu hóa.");
        } catch (BadCredentialsException ex) {
            throw new BadCredentialsException("Email hoặc mật khẩu không đúng.");
        }
    }

    // ─── Refresh Token (Rotation) ──────────────────────────────────────────────

    /**
     * noRollbackFor: các lệnh thu hồi token ở nhánh lỗi PHẢI được commit.
     * Nếu không, exception sẽ rollback và việc thu hồi không có tác dụng.
     */
    @Transactional(noRollbackFor = { InvalidTokenException.class, UserLockedException.class })
    public AuthResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String tokenHash = sha256Hex(request.refreshToken());

        RefreshToken stored = refreshTokenRepo.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token không hợp lệ."));

        LocalDateTime now = LocalDateTime.now();

        // Phát hiện dùng lại token đã bị thay thế → thu hồi toàn bộ session
        if (stored.getReplacedById() != null) {
            log.warn("Phát hiện reuse refresh token của user {}. Thu hồi toàn bộ session.",
                    stored.getUser().getId());
            refreshTokenRepo.revokeAllByUserId(stored.getUser().getId(), now);
            throw new InvalidTokenException("Token đã hết hạn. Vui lòng đăng nhập lại.");
        }

        if (!stored.isValid()) {
            throw new InvalidTokenException("Refresh token đã hết hạn hoặc bị thu hồi.");
        }

        // Kiểm tra trạng thái user: người bị khóa/vô hiệu hóa không được refresh
        User user = stored.getUser();
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            stored.setRevokedAt(now);
            refreshTokenRepo.save(stored);
            throw new UserLockedException("Tài khoản đã bị khóa hoặc vô hiệu hóa.");
        }

        // Rotation: thu hồi token cũ, phát hành token mới
        stored.setRevokedAt(now);
        IssuedToken issued = createRefreshToken(user, ipOf(httpRequest), userAgentOf(httpRequest));
        stored.setReplacedById(issued.entity().getId());
        refreshTokenRepo.save(stored);

        String accessToken = jwtProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());

        return AuthResponse.of(
                accessToken,
                issued.rawToken(),
                jwtProvider.getAccessTokenExpirationMs() / 1000,
                userMapper.toResponse(user));
    }

    // ─── Đăng xuất thiết bị hiện tại ─────────────────────────────────────────

    @Transactional
    public void logout(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            return;
        }
        refreshTokenRepo.findByTokenHash(sha256Hex(rawRefreshToken)).ifPresent(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(LocalDateTime.now());
                refreshTokenRepo.save(rt);
            }
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

        IssuedToken issued = createRefreshToken(user, ip, userAgent);

        return AuthResponse.of(
                accessToken,
                issued.rawToken(),
                jwtProvider.getAccessTokenExpirationMs() / 1000,
                userMapper.toResponse(user));
    }

    /**
     * Sinh refresh token ngẫu nhiên 256 bit, chỉ lưu hash SHA-256 vào DB.
     * Token gốc được trả qua record, KHÔNG gắn vào bất kỳ field nào của entity.
     */
    private IssuedToken createRefreshToken(User user, String ip, String userAgent) {
        String rawToken = generateSecureToken();

        RefreshToken saved = refreshTokenRepo.save(RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays))
                .ipAddress(ip)
                .userAgent(userAgent)
                .build());

        return new IssuedToken(saved, rawToken);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng", e);
        }
    }

    /**
     * Header X-Forwarded-For có thể bị giả mạo; chỉ nên tin khi chạy sau reverse
     * proxy
     * đã biết. Luôn cắt độ dài để không vượt cột VARCHAR(45).
     */
    private String ipOf(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        String ip = StringUtils.hasText(xff) ? xff.split(",")[0].trim() : req.getRemoteAddr();
        return truncate(ip, MAX_IP_LENGTH);
    }

    private String userAgentOf(HttpServletRequest req) {
        return truncate(req.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH);
    }

    private String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}