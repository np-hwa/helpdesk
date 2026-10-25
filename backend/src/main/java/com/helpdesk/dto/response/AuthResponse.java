package com.helpdesk.dto.response;

/**
 * Kết quả đăng nhập thành công.
 * accessToken: JWT ngắn hạn (15 phút).
 * refreshToken: token dài hạn (7 ngày) để gia hạn access token.
 */
public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,     // giây
    UserResponse user
) {
    public static AuthResponse of(String access, String refresh, long expiresIn, UserResponse user) {
        return new AuthResponse(access, refresh, "Bearer", expiresIn, user);
    }
}
