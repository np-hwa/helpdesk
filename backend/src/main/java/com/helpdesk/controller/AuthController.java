package com.helpdesk.controller;

import com.helpdesk.domain.entity.User;
import com.helpdesk.dto.request.ChangePasswordRequest;
import com.helpdesk.dto.request.LoginRequest;
import com.helpdesk.dto.request.RefreshTokenRequest;
import com.helpdesk.dto.response.ApiResponse;
import com.helpdesk.dto.response.AuthResponse;
import com.helpdesk.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints xác thực: đăng nhập, refresh, đăng xuất.
 *
 * POST /api/auth/login         → Đăng nhập, nhận access + refresh token
 * POST /api/auth/refresh       → Làm mới access token (rotation)
 * POST /api/auth/logout        → Đăng xuất thiết bị hiện tại
 * POST /api/auth/logout-all    → Đăng xuất tất cả thiết bị
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Đăng nhập, refresh token, đăng xuất")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = "Trả về access token (15 phút) và refresh token (7 ngày)")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse auth = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", auth));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới access token", description = "Dùng refresh token để lấy access token mới (token rotation)")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse auth = authService.refreshToken(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Làm mới token thành công", auth));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất thiết bị hiện tại")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest request) {
        String rawToken = request != null ? request.refreshToken() : null;
        authService.logout(rawToken);
        return ResponseEntity.ok(ApiResponse.ok("Đăng xuất thành công"));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Đăng xuất tất cả thiết bị")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @AuthenticationPrincipal User currentUser) {
        authService.logoutAll(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok("Đã đăng xuất tất cả thiết bị"));
    }
}
