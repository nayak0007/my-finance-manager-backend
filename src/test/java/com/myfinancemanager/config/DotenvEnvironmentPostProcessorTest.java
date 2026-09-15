package com.myfinancemanager.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DotenvEnvironmentPostProcessorTest {

    @TempDir
    Path tempDir;

    private final SpringApplication application = new SpringApplication();

    @Test
    void loadsKeyValuePairsFromEnvFile() throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.write(envFile, List.of(
                "# comment",
                "",
                "export SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/myfinance",
                "JWT_SECRET=\"secret-with # hash\"",
                "OPENROUTER_MODEL='openai/gpt-4o-mini'",
                "CORS_ALLOWED_ORIGINS=http://localhost:3000 # inline comment",
                "BROKEN_LINE_WITHOUT_EQUALS"));

        MockEnvironment environment = new MockEnvironment();
        new DotenvEnvironmentPostProcessor(envFile).postProcessEnvironment(environment, application);

        assertThat(environment.getProperty("SPRING_DATASOURCE_URL"))
                .isEqualTo("jdbc:postgresql://db-host:5432/myfinance");
        assertThat(environment.getProperty("JWT_SECRET")).isEqualTo("secret-with # hash");
        assertThat(environment.getProperty("OPENROUTER_MODEL")).isEqualTo("openai/gpt-4o-mini");
        assertThat(environment.getProperty("CORS_ALLOWED_ORIGINS")).isEqualTo("http://localhost:3000");
        assertThat(environment.getProperty("BROKEN_LINE_WITHOUT_EQUALS")).isNull();
    }

    @Test
    void realEnvironmentVariablesWinOverEnvFile() throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.write(envFile, List.of("JWT_ISSUER=from-dotenv"));

        MockEnvironment environment = new MockEnvironment().withProperty("JWT_ISSUER", "from-system");
        new DotenvEnvironmentPostProcessor(envFile).postProcessEnvironment(environment, application);

        assertThat(environment.getProperty("JWT_ISSUER")).isEqualTo("from-system");
    }

    @Test
    void missingEnvFileIsIgnored() {
        MockEnvironment environment = new MockEnvironment();

        new DotenvEnvironmentPostProcessor(tempDir.resolve("does-not-exist.env"))
                .postProcessEnvironment(environment, application);

        assertThat(environment.getPropertySources().contains(DotenvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isFalse();
    }

    @Test
    void dotEnvPathOverrideIsHonored() throws Exception {
        Path envFile = tempDir.resolve("custom.env");
        Files.write(envFile, List.of("STORAGE_LOCATION=/tmp/uploads"));

        MockEnvironment environment = new MockEnvironment().withProperty("DOTENV_PATH", envFile.toString());
        new DotenvEnvironmentPostProcessor(tempDir.resolve("unused.env"))
                .postProcessEnvironment(environment, application);

        assertThat(environment.getProperty("STORAGE_LOCATION")).isEqualTo("/tmp/uploads");
    }
}
