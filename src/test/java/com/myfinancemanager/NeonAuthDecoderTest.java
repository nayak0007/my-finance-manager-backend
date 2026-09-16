package com.myfinancemanager;

import com.myfinancemanager.config.NeonAuthConfig;
import com.myfinancemanager.config.NeonAuthProperties;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the production decoder against the exact shape Neon Auth produces.
 *
 * <p>Neon Auth signs with EdDSA (Ed25519), which Spring Security 6.3 cannot request through its
 * fluent API and which nimbus-jose-jwt performs via Tink. Both of those are easy to get wrong in
 * a way that no compile error surfaces, so they are asserted here directly.
 */
class NeonAuthDecoderTest {

    private static final NeonAuthProperties PROPERTIES =
            new NeonAuthProperties(NeonAuthTestSupport.baseUrl(), null, null);

    @Test
    void issuerAndJwksUriAreDerivedFromTheBaseUrl() {
        assertThat(PROPERTIES.issuer()).isEqualTo(NeonAuthTestSupport.issuer());
        assertThat(PROPERTIES.jwksUri())
                .isEqualTo(NeonAuthTestSupport.baseUrl() + "/.well-known/jwks.json");
        // The database segment belongs to the path, never to the issuer.
        assertThat(PROPERTIES.issuer()).doesNotContain("/testdb/auth");
    }

    @Test
    void jwksEndpointServesTheKeyTheDecoderNeeds() throws Exception {
        JWKSet published = JWKSet.load(URI.create(PROPERTIES.jwksUri()).toURL());

        assertThat(published.getKeys()).hasSize(1);
        JWK key = published.getKeys().get(0);
        assertThat(key.getKeyID()).isEqualTo(NeonAuthTestSupport.KEY_ID);
        assertThat(key.getKeyType().getValue()).isEqualTo("OKP");
    }

    @Test
    void aFreshlyMintedTokenVerifies() {
        String subject = UUID.randomUUID().toString();
        String token = NeonAuthTestSupport.token(subject, "decoder-probe@example.com");

        Jwt jwt = decoder().decode(token);

        assertThat(jwt.getSubject()).isEqualTo(subject);
        assertThat(jwt.getClaimAsString("email")).isEqualTo("decoder-probe@example.com");
        assertThat(jwt.getAudience()).containsExactly(NeonAuthTestSupport.issuer());
    }

    @Test
    void tokensSignedByAnUnpublishedKeyAreRejected() {
        assertThatThrownBy(() -> decoder().decode(
                NeonAuthTestSupport.tokenSignedByUnknownKey(UUID.randomUUID().toString(), "x@example.com")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokensForAnotherAudienceAreRejected() {
        assertThatThrownBy(() -> decoder().decode(
                NeonAuthTestSupport.tokenWithWrongAudience(UUID.randomUUID().toString(), "x@example.com")))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void expiredTokensAreRejected() {
        assertThatThrownBy(() -> decoder().decode(
                NeonAuthTestSupport.expiredToken(UUID.randomUUID().toString(), "x@example.com")))
                .isInstanceOf(JwtException.class);
    }

    private JwtDecoder decoder() {
        return new NeonAuthConfig().neonAuthJwtDecoder(PROPERTIES);
    }
}
