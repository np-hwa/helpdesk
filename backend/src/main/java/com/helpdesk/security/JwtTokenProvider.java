package com.helpdesk.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Tiện ích tạo và xác thực JWT Access Token.
 * <p>
 * Sử dụng thuật toán HS256 với secret key ≥ 256 bit (32 bytes).
 * Access token chứa: sub (email), userId, role.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = expirationMs;
    }

    /** Tạo Access JWT chứa userId và role. */
    public String generateAccessToken(Long userId, String email, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(email)
                .claims(Map.of(
                    "userId", userId,
                    "role",   role
                ))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /** Lấy email (subject) từ token. */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** Lấy userId từ token. */
    public Long getUserIdFromToken(String token) {
        Object val = parseClaims(token).get("userId");
        if (val instanceof Integer i) return i.longValue();
        if (val instanceof Long l)    return l;
        throw new JwtException("Không tìm thấy userId trong token");
    }

    /** Lấy role từ token. */
    public String getRoleFromToken(String token) {
        return (String) parseClaims(token).get("role");
    }

    /** Xác thực token: chữ ký + hết hạn. */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT không hợp lệ: {}", ex.getMessage());
            return false;
        }
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    // ─── private ──────────────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
