package com.helpdesk.dto.request;

import com.helpdesk.domain.enums.Role;
import jakarta.validation.constraints.*;

/**
 * Payload tạo tài khoản người dùng mới (dùng bởi ADMIN).
 */
public record CreateUserRequest(
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 150, message = "Email tối đa 150 ký tự")
    String email,

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, max = 72, message = "Mật khẩu từ 8–72 ký tự")
    String password,

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    String fullName,

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    String phone,

    Long departmentId,

    @NotNull(message = "Vai trò không được để trống")
    Role role
) {}
