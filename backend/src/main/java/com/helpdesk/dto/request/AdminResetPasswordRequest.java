package com.helpdesk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload đặt lại mật khẩu bằng ADMIN (không cần mật khẩu cũ). */
public record AdminResetPasswordRequest(
    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 8, max = 72, message = "Mật khẩu từ 8–72 ký tự")
    String newPassword
) {}
