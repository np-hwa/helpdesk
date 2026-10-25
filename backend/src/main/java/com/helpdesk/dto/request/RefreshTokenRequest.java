package com.helpdesk.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Payload làm mới access token bằng refresh token. */
public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token không được để trống")
    String refreshToken
) {}
