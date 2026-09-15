package com.myfinancemanager.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Normalizes the {@code DATABASE_URL} connection string (as provided by Render and other
 * platforms) into the standard Spring datasource properties. Accepted formats:
 * {@code postgres://}, {@code postgresql://} and {@code jdbc:postgresql://}.
 *
 * <p>The processor is skipped only when the datasource URL was configured explicitly
 * (a real {@code SPRING_DATASOURCE_URL} value or a literal {@code spring.datasource.url}
 * entry in a config file). A placeholder default in application.yml &mdash;
 * {@code ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/myfinance}} &mdash; does
 * <em>not</em> count as explicit configuration, otherwise {@code DATABASE_URL} could never
 * override the built-in fallback.</p>
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE = "databaseUrlNormalizer";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        if (explicitDatasourceUrlConfigured(environment)) {
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

    /**
     * Whether a datasource URL was deliberately configured. A placeholder with a default in a
     * config file (e.g. {@code ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/myfinance}})
     * is not deliberate configuration: the normalizer must still run so that {@code DATABASE_URL}
     * can override the built-in fallback.
     */
    private boolean explicitDatasourceUrlConfigured(ConfigurableEnvironment environment) {
        String springDatasourceUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        if (springDatasourceUrl != null && !springDatasourceUrl.isBlank()) {
            return true;
        }
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source.containsProperty("spring.datasource.url")) {
                Object raw = source.getProperty("spring.datasource.url");
                if (raw != null && !raw.toString().contains("${")) {
                    return true;
                }
            }
        }
        return false;
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
