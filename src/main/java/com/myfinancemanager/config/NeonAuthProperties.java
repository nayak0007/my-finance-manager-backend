package com.myfinancemanager.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * Connection details for Neon Auth (Managed Better Auth), which owns user identity.
 *
 * <p>Only {@code base-url} is required; {@code issuer} and {@code jwks-uri} are derived from it
 * so the three can never drift apart. They stay overridable for the rare case where the
 * service is fronted by a different hostname.
 *
 * <p>A base URL looks like
 * {@code https://<endpoint>.neonauth.<region>.aws.neon.tech/<database>/auth} — note that the
 * database segment is part of the path, while the JWT's {@code iss}/{@code aud} claims are the
 * origin only, without {@code /<database>/auth}.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth.neon")
public record NeonAuthProperties(
        @NotBlank String baseUrl,
        String issuer,
        String jwksUri
) {
    public NeonAuthProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            // Fail with the env var's name rather than letting URI.create report a bare
            // "missing scheme or host", which gives no hint about what to set.
            throw new IllegalStateException(
                    "app.auth.neon.base-url is required. Set NEON_AUTH_URL to the Neon Auth base URL, "
                            + "e.g. https://<endpoint>.neonauth.<region>.aws.neon.tech/<database>/auth");
        }
        baseUrl = stripTrailingSlash(baseUrl);
        if (issuer == null || issuer.isBlank()) {
            issuer = originOf(baseUrl);
        }
        if (jwksUri == null || jwksUri.isBlank()) {
            jwksUri = baseUrl + "/.well-known/jwks.json";
        }
    }

    private static String stripTrailingSlash(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "");
    }

    /** {@code https://host/<db>/auth} -> {@code https://host}, matching the JWT's iss claim. */
    private static String originOf(String url) {
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getAuthority() == null) {
                throw new IllegalArgumentException("missing scheme or host");
            }
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "app.auth.neon.base-url must be an absolute URL such as "
                            + "https://<endpoint>.neonauth.<region>.aws.neon.tech/<database>/auth"
                            + " but was: " + url, ex);
        }
    }
}
