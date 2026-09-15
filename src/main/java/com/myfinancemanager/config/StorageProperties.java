package com.myfinancemanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String location
) {
    public StorageProperties {
        if (location == null || location.isBlank()) {
            location = "./data/uploads";
        }
    }
}
