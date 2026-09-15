package com.myfinancemanager.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads environment variables from a {@code .env} file (as created from {@code .env.example})
 * so local development does not require exporting variables manually. Spring Boot does not
 * read {@code .env} files on its own.
 *
 * <p>The file is looked up at {@code ${user.dir}/.env}, or at {@code DOTENV_PATH} when set.
 * Values are registered with the <em>lowest</em> precedence: real environment variables,
 * system properties and explicit {@code application.yml} entries always win. Placeholders
 * in application.yml such as {@code ${SPRING_DATASOURCE_URL:default}} still resolve against
 * values defined here, which is what makes the file effective.</p>
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "dotenvProperties";

    private final Path defaultLocation;

    public DotenvEnvironmentPostProcessor() {
        this(Paths.get(System.getProperty("user.dir"), ".env"));
    }

    DotenvEnvironmentPostProcessor(Path location) {
        this.defaultLocation = location;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Path location = resolveLocation(environment);
        if (location == null || !Files.isRegularFile(location)) {
            return;
        }
        Map<String, Object> properties = parse(location);
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    private Path resolveLocation(ConfigurableEnvironment environment) {
        String override = environment.getProperty("DOTENV_PATH");
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim());
        }
        return defaultLocation;
    }

    private Map<String, Object> parse(Path location) {
        Map<String, Object> properties = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(location);
            for (String rawLine : lines) {
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).strip();
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = line.substring(0, separator).strip();
                String value = unquote(line.substring(separator + 1).strip());
                properties.put(key, value);
            }
        } catch (IOException ex) {
            // An unreadable .env file must not prevent startup; real env vars still apply.
        }
        return properties;
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        int comment = value.indexOf(" #");
        return comment >= 0 ? value.substring(0, comment).strip() : value;
    }
}
