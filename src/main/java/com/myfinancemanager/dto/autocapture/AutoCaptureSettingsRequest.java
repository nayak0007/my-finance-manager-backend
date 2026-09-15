package com.myfinancemanager.dto.autocapture;

import java.util.List;

public record AutoCaptureSettingsRequest(
        Boolean enabled,
        Boolean smsEnabled,
        Boolean emailEnabled,
        List<String> senderAllowList,
        List<String> senderBlockList
) {
}
