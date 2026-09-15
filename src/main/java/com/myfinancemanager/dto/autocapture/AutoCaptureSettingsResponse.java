package com.myfinancemanager.dto.autocapture;

import com.myfinancemanager.domain.AutoCaptureSettings;

import java.time.Instant;
import java.util.List;

public record AutoCaptureSettingsResponse(
        boolean enabled,
        boolean smsEnabled,
        boolean emailEnabled,
        List<String> senderAllowList,
        List<String> senderBlockList,
        Instant updatedAt
) {
    public static AutoCaptureSettingsResponse from(AutoCaptureSettings settings) {
        return new AutoCaptureSettingsResponse(
                settings.isEnabled(),
                settings.isSmsEnabled(),
                settings.isEmailEnabled(),
                settings.getSenderAllowList(),
                settings.getSenderBlockList(),
                settings.getUpdatedAt());
    }
}
