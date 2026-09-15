package com.myfinancemanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.google")
public record GoogleProperties(
        String clientId
) {
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }
}
