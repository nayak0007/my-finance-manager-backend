package com.myfinancemanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.rapidapi")
public record RapidApiProperties(
        String apiKey,
        String host,
        String url,
        String fileFieldName,
        Duration timeout
) {
    public RapidApiProperties {
        if (timeout == null) {
            timeout = Duration.ofSeconds(60);
        }
        if (fileFieldName == null || fileFieldName.isBlank()) {
            fileFieldName = "file";
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && url != null && !url.isBlank();
    }
}
