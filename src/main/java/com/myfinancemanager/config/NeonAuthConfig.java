package com.myfinancemanager.config;

import com.myfinancemanager.security.NeonAuthJwtDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;

/**
 * Builds the {@link JwtDecoder} used to authenticate every API request.
 *
 * <p>Validation is deliberately strict: expiry and not-before come from
 * {@link JwtValidators#createDefaultWithIssuer}, which also requires the issuer, and the
 * audience must name this same Neon Auth instance. Without the audience check, a token minted
 * for another Neon project that shares a signing key could be replayed here.
 *
 * <p>See {@link NeonAuthJwtDecoder} for why the decoding itself is not the stock Nimbus
 * implementation.
 */
@Configuration
public class NeonAuthConfig {

    @Bean
    public JwtDecoder neonAuthJwtDecoder(NeonAuthProperties properties) {
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                audienceMustMatch(properties.issuer()));
        return new NeonAuthJwtDecoder(toUrl(properties.jwksUri()), validator);
    }

    /**
     * Neon Auth sets {@code aud} to the auth service origin, i.e. the same value as {@code iss}.
     * Spring maps the claim to a list, but a single-value claim is legal JWT, so both shapes
     * are accepted.
     */
    private OAuth2TokenValidator<Jwt> audienceMustMatch(String expectedAudience) {
        return token -> {
            List<String> audience = token.getAudience();
            if (audience != null && audience.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "The token audience does not name this Neon Auth instance",
                    null));
        };
    }

    private static URL toUrl(String jwksUri) {
        try {
            return URI.create(jwksUri).toURL();
        } catch (IllegalArgumentException | MalformedURLException ex) {
            throw new IllegalStateException(
                    "app.auth.neon.jwks-uri is not a valid URL: " + jwksUri, ex);
        }
    }
}
