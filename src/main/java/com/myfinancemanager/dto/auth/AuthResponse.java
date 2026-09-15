package com.myfinancemanager.dto.auth;

import com.myfinancemanager.dto.user.UserResponse;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user
) {
}
