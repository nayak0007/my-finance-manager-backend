package com.myfinancemanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.openrouter")
public record OpenRouterProperties(
        String apiKey,
        String baseUrl,
        String model,
        Duration timeout
) {
    public OpenRouterProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://openrouter.ai/api/v1";
        }
        if (model == null || model.isBlank()) {
            model = "openai/gpt-4o-mini";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(60);
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
