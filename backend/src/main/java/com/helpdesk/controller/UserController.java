package com.helpdesk.controller;

import com.helpdesk.domain.entity.User;
import com.helpdesk.domain.enums.Role;
import com.helpdesk.domain.enums.UserStatus;
import com.helpdesk.dto.request.AdminResetPasswordRequest;
import com.helpdesk.dto.request.ChangePasswordRequest;
import com.helpdesk.dto.request.CreateUserRequest;
import com.helpdesk.dto.request.UpdateUserRequest;
import com.helpdesk.dto.response.ApiResponse;
import com.helpdesk.dto.response.UserResponse;
import com.helpdesk.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API quản lý tài khoản người dùng và phân quyền.
 *
 * ── Admin (chỉ ADMIN) ─────────────────────────────────────────────────────────
 * GET    /api/users                    Danh sách user (filter + phân trang)
 * GET    /api/users/{id}               Thông tin một user
 * POST   /api/users                    Tạo tài khoản mới
 * PUT    /api/users/{id}               Cập nhật thông tin user
 * PUT    /api/users/{id}/status        Đổi trạng thái (ACTIVE/INACTIVE/LOCKED)
 * POST   /api/users/{id}/reset-password Admin đặt lại mật khẩu
 *
 * ── IT Support list (ADMIN, IT_LEADER) ────────────────────────────────────────
 * GET    /api/users/it-support/active  Danh sách IT Support đang ACTIVE (để phân công)
 *
 * ── Profile (bất kỳ user đã đăng nhập) ───────────────────────────────────────
 * GET    /api/users/me                 Thông tin profile bản thân
 * POST   /api/users/me/change-password Đổi mật khẩu bản thân
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Management", description = "Quản lý tài khoản và phân quyền")
public class UserController {

    private final UserService userService;

    // ─── Danh sách user ───────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Danh sách người dùng (ADMIN, IT_LEADER)")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(
            @Parameter(description = "Lọc theo vai trò")
            @RequestParam(required = false) Role role,
            @Parameter(description = "Lọc theo trạng thái")
            @RequestParam(required = false) UserStatus status,
            @Parameter(description = "Tìm kiếm theo tên/email")
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<UserResponse> page = userService.getUsers(role, status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    // ─── Lấy IT Support ACTIVE (phân công) ───────────────────────────────────

    @GetMapping("/it-support/active")
    @Operation(summary = "Danh sách IT Support đang hoạt động (dùng cho phân công)")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getActiveItSupport() {
        return ResponseEntity.ok(ApiResponse.ok(userService.getActiveItSupportList()));
    }

    // ─── Profile bản thân ─────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Thông tin tài khoản của tôi")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyProfile(currentUser.getId())));
    }

    @PostMapping("/me/change-password")
    @Operation(summary = "Đổi mật khẩu của tôi")
    public ResponseEntity<ApiResponse<Void>> changeMyPassword(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Đổi mật khẩu thành công. Vui lòng đăng nhập lại."));
    }

    // ─── Admin: CRUD user ─────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Thông tin một người dùng (ADMIN, IT_LEADER)")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserById(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo tài khoản mới (ADMIN)")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Tạo tài khoản thành công", created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin người dùng (ADMIN)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thành công", userService.updateUser(id, request)));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Thay đổi trạng thái tài khoản (ADMIN): ACTIVE | INACTIVE | LOCKED")
    public ResponseEntity<ApiResponse<UserResponse>> changeStatus(
            @PathVariable Long id,
            @RequestParam UserStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Đổi trạng thái thành công", userService.changeStatus(id, status)));
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Admin đặt lại mật khẩu người dùng (ADMIN)")
    public ResponseEntity<ApiResponse<Void>> adminResetPassword(
            @PathVariable Long id,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        userService.adminResetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Đặt lại mật khẩu thành công. User cần đăng nhập lại."));
    }
}
