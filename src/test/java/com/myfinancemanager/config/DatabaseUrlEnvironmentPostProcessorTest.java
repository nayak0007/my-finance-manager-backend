package com.myfinancemanager.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    void normalizesPostgresUrlIntoJdbcProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://myfinance:s3cret@db-host:5433/myfinance");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db-host:5433/myfinance");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("myfinance");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("s3cret");
    }

    @Test
    void preservesQueryParametersAndAppendsSslMode() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL",
                        "postgres://user:p%40ss@neon-host/myfinance?sslmode=require")
                .withProperty("DATABASE_SSL_MODE", "verify-full");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://neon-host:5432/myfinance?sslmode=require");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("p@ss");
    }

    @Test
    void appendsSslModeWhenMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgres://user:pw@host/db")
                .withProperty("DATABASE_SSL_MODE", "require");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://host:5432/db?sslmode=require");
    }

    @Test
    void leavesExistingJdbcUrlUntouchedWhenConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://user:pw@host/db")
                .withProperty("spring.datasource.url", "jdbc:postgresql://explicit-host/db");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://explicit-host/db");
    }
}
