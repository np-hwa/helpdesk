package com.helpdesk.dto.response;

import com.helpdesk.domain.enums.Role;
import com.helpdesk.domain.enums.UserStatus;

import java.time.LocalDateTime;

/**
 * Thông tin người dùng trả về cho client.
 * Không bao giờ trả về passwordHash.
 */
public record UserResponse(
    Long id,
    String email,
    String fullName,
    String phone,
    Long departmentId,
    String departmentName,
    Role role,
    UserStatus status,
    Boolean emailNotify,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
