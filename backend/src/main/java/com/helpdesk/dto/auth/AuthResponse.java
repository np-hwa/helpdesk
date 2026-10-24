package com.helpdesk.dto.auth;

import com.helpdesk.enums.Role;
import lombok.Builder;

@Builder
public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    Long id,
    String email,
    String fullName,
    Role role,
    Long departmentId,
    String departmentName
) {
    // Constructor để thiết lập giá trị mặc định cho tokenType khi dùng @Builder
    public AuthResponse {
        if (tokenType == null) {
            tokenType = "Bearer";
        }
    }
}
