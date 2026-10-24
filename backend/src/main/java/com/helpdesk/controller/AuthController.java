package com.helpdesk.controller;

import com.helpdesk.dto.auth.AuthResponse;
import com.helpdesk.dto.auth.LoginRequest;
import com.helpdesk.dto.auth.RefreshTokenRequest;
import com.helpdesk.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // Với JWT stateless, logout chủ yếu được xử lý ở client (xóa token khỏi localStorage/sessionStorage).
        // Endpoint này xác nhận phía server thành công.
        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));
    }
}
