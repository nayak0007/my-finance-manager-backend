package com.myfinancemanager.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the configuration-time failure modes of the Neon Auth settings.
 *
 * <p>The empty base URL is the one that matters in practice: a missing {@code NEON_AUTH_URL} used
 * to surface as a bare "missing scheme or host" from deep inside {@link java.net.URI}, which gave
 * no hint about which variable to set.
 */
class NeonAuthPropertiesTest {

    @Test
    void blankBaseUrlNamesTheMissingVariable() {
        assertThatThrownBy(() -> new NeonAuthProperties("", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NEON_AUTH_URL");
    }

    @Test
    void nullBaseUrlNamesTheMissingVariable() {
        assertThatThrownBy(() -> new NeonAuthProperties(null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NEON_AUTH_URL");
    }

    @Test
    void issuerAndJwksUriAreDerivedFromBaseUrl() {
        NeonAuthProperties properties = new NeonAuthProperties(
                "https://ep-delicate-mud-b3ht2w29.neonauth.c-4.ap-southeast-1.aws.neon.tech/neondb/auth/",
                null, null);

        assertThat(properties.baseUrl()).isEqualTo(
                "https://ep-delicate-mud-b3ht2w29.neonauth.c-4.ap-southeast-1.aws.neon.tech/neondb/auth");
        // The iss/aud claim is the origin only, without the /<database>/auth path.
        assertThat(properties.issuer()).isEqualTo(
                "https://ep-delicate-mud-b3ht2w29.neonauth.c-4.ap-southeast-1.aws.neon.tech");
        assertThat(properties.jwksUri()).isEqualTo(
                "https://ep-delicate-mud-b3ht2w29.neonauth.c-4.ap-southeast-1.aws.neon.tech/neondb/auth/.well-known/jwks.json");
    }

    @Test
    void explicitIssuerAndJwksUriAreNotOverridden() {
        NeonAuthProperties properties = new NeonAuthProperties(
                "https://ep-delicate-mud-b3ht2w29.neonauth.c-4.ap-southeast-1.aws.neon.tech/neondb/auth",
                "https://issuer.example.com", "https://jwks.example.com/jwks.json");

        assertThat(properties.issuer()).isEqualTo("https://issuer.example.com");
        assertThat(properties.jwksUri()).isEqualTo("https://jwks.example.com/jwks.json");
    }
}
