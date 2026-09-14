package com.myfinancemanager.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Normalizes the {@code DATABASE_URL} connection string (as provided by Render and other
 * platforms) into the standard Spring datasource properties. Accepted formats:
 * {@code postgres://}, {@code postgresql://} and {@code jdbc:postgresql://}.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE = "databaseUrlNormalizer";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        if (environment.getProperty("spring.datasource.url") != null) {
            return;
        }

        try {
            Map<String, Object> properties = new HashMap<>();
            String sslMode = environment.getProperty("DATABASE_SSL_MODE");
            if (databaseUrl.startsWith("jdbc:postgresql://")) {
                properties.put("spring.datasource.url", appendSslMode(databaseUrl, sslMode));
            } else {
                String normalized = databaseUrl.replaceFirst("^postgres(ql)?://", "http://");
                URI uri = URI.create(normalized);
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath() == null ? "" : uri.getPath();
                StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                        .append(host).append(':').append(port).append(path);
                if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                    jdbcUrl.append('?').append(uri.getQuery());
                }
                properties.put("spring.datasource.url", appendSslMode(jdbcUrl.toString(), sslMode));

                String userInfo = uri.getUserInfo();
                if (userInfo != null && !userInfo.isBlank()) {
                    String[] parts = userInfo.split(":", 2);
                    properties.put("spring.datasource.username", decode(parts[0]));
                    if (parts.length > 1) {
                        properties.put("spring.datasource.password", decode(parts[1]));
                    }
                }
            }
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, properties));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Unable to parse DATABASE_URL into JDBC properties", ex);
        }
    }

    private String appendSslMode(String jdbcUrl, String sslMode) {
        if (sslMode == null || sslMode.isBlank() || jdbcUrl.contains("sslmode=")) {
            return jdbcUrl;
        }
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=" + sslMode;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
