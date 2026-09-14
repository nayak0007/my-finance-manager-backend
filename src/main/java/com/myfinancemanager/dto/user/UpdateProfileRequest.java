package com.myfinancemanager.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateProfileRequest(
        @Size(max = 200) String fullName,
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter ISO code") String currency,
        Map<String, Object> preferences,
        Boolean notificationsEnabled
) {
}
