package com.helpdesk.service;

import com.helpdesk.dto.auth.AuthResponse;
import com.helpdesk.dto.auth.LoginRequest;
import com.helpdesk.dto.auth.RefreshTokenRequest;
import com.helpdesk.entity.RefreshToken;
import com.helpdesk.entity.User;
import com.helpdesk.exception.InvalidTokenException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.RefreshTokenRepository;
import com.helpdesk.repository.UserRepository;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${helpdesk.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    // Record phụ trợ giữ cả chuỗi token gốc và đối tượng Entity vừa lưu (để lấy ID)
    private record SavedRefreshToken(String rawToken, RefreshToken entity) {}

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Lỗi thuật toán mã hóa SHA-256", e);
        }
    }

    //Đổi kiểu trả về thành SavedRefreshToken để lấy được ID của bản ghi mới
    private SavedRefreshToken createAndSaveRefreshToken(User user) {
        String rawRefreshToken = tokenProvider.generateRefreshToken(user.getEmail());
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .build();

        RefreshToken savedEntity = refreshTokenRepository.save(Objects.requireNonNull(refreshTokenEntity));
        return new SavedRefreshToken(rawRefreshToken, savedEntity);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userDetails.getEmail()));

        String accessToken = tokenProvider.generateAccessToken(authentication);
        
        // Gọi hàm mới và lấy chuỗi rawToken
        String refreshToken = createAndSaveRefreshToken(user).rawToken();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .id(userDetails.getId())
                .email(userDetails.getEmail())
                .fullName(userDetails.getFullName())
                .role(userDetails.getRole())
                .departmentId(userDetails.getDepartmentId())
                .departmentName(userDetails.getDepartmentName())
                .build();
    }

    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String rawRefreshToken = request.getRefreshToken();

        if (!tokenProvider.validateToken(rawRefreshToken) 
                || !"REFRESH".equals(tokenProvider.getTokenType(rawRefreshToken))) {
            throw new InvalidTokenException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        String tokenHash = hashToken(rawRefreshToken);
        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token không tồn tại trong hệ thống"));

        LocalDateTime now = LocalDateTime.now();

        if (tokenEntity.isRevoked()) {
            refreshTokenRepository.revokeAllUserTokens(tokenEntity.getUser(), now);
            throw new InvalidTokenException("Phát hiện token đã bị thu hồi. Vui lòng đăng nhập lại!");
        }

        if (tokenEntity.getExpiresAt().isBefore(now)) {
            throw new InvalidTokenException("Refresh token đã hết hạn");
        }

        User user = tokenEntity.getUser();

        // 1. Tạo và lưu Refresh Token mới xuống DB trước để lấy ID của token mới
        SavedRefreshToken newRefresh = createAndSaveRefreshToken(user);

        // 2. Thu hồi token cũ và gán replaced_by_id trỏ sang ID của token mới vừa tạo
        tokenEntity.setRevokedAt(now);
        tokenEntity.setReplacedById(newRefresh.entity().getId());
        refreshTokenRepository.save(tokenEntity);

        // 3. Sinh Access Token mới và lấy chuỗi Refresh Token mới trả về cho client
        String newAccessToken = tokenProvider.generateAccessTokenFromEmail(user.getEmail());
        String newRefreshToken = newRefresh.rawToken();

        String deptName = (user.getDepartment() != null) ? user.getDepartment().getName() : null;
        Long deptId = (user.getDepartment() != null) ? user.getDepartment().getId() : null;

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .departmentId(deptId)
                .departmentName(deptName)
                .build();
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                if (!token.isRevoked()) {
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                }
            });
        }
    }
}