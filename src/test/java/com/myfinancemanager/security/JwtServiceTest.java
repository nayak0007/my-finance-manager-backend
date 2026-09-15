package com.myfinancemanager.security;

import com.myfinancemanager.config.JwtProperties;
import com.myfinancemanager.domain.AuthProvider;
import com.myfinancemanager.domain.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new JwtProperties(
            "unit-test-secret-key-with-enough-entropy-1234567890",
            "my-finance-manager-test",
            Duration.ofMinutes(15),
            Duration.ofDays(30)));

    @Test
    void generatesAndParsesAccessToken() {
        User user = new User();
        UUID id = UUID.randomUUID();
        user.setId(id);
        user.setEmail("user@example.com");
        user.setAuthProvider(AuthProvider.LOCAL);

        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.parseUserId(token)).isEqualTo(id);
    }

    @Test
    void rejectsTamperedToken() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");

        String token = jwtService.generateAccessToken(user);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void generatesOpaqueRefreshTokens() {
        String first = jwtService.generateRefreshTokenValue();
        String second = jwtService.generateRefreshTokenValue();

        assertThat(first).isNotBlank().isNotEqualTo(second);
    }
}
