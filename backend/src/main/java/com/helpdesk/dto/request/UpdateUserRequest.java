package com.helpdesk.dto.request;

import com.helpdesk.domain.enums.Role;
import com.helpdesk.domain.enums.UserStatus;
import jakarta.validation.constraints.*;

/**
 * Payload cập nhật thông tin người dùng (dùng bởi ADMIN).
 * Mật khẩu được đặt lại qua endpoint riêng (ChangePasswordRequest).
 */
public record UpdateUserRequest(
    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100)
    String fullName,

    @Size(max = 20)
    String phone,

    Long departmentId,

    @NotNull(message = "Vai trò không được để trống")
    Role role,

    @NotNull(message = "Trạng thái không được để trống")
    UserStatus status,

    Boolean emailNotify
) {}
