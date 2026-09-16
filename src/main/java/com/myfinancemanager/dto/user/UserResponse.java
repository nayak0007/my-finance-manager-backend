package com.myfinancemanager.dto.user;

import com.myfinancemanager.domain.User;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String currency,
        boolean emailVerified,
        boolean notificationsEnabled,
        Map<String, Object> preferences,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getCurrency(),
                user.isEmailVerified(),
                user.isNotificationsEnabled(),
                user.getPreferences(),
                user.getCreatedAt());
    }
}
