package com.myfinancemanager.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Verifies Neon Auth access tokens.
 *
 * <p>Neon Auth signs with EdDSA (Ed25519), and this decoder exists because Spring Security's
 * {@link org.springframework.security.oauth2.jwt.NimbusJwtDecoder} cannot handle that on the
 * Nimbus version Spring Boot 3.3 manages. Two independent limitations apply to
 * nimbus-jose-jwt 9.37.3:
 *
 * <ol>
 *   <li>{@code DefaultJWSVerifierFactory} only knows RSA, ECDSA and MAC - it has no EdDSA
 *       branch, so no verifier can be produced for an EdDSA token at all;</li>
 *   <li>every JWK-set key selector converts matched JWKs to {@code java.security.Key}, and
 *       {@code OctetKeyPair.toPublicKey()} throws "Export to java.security.PublicKey not
 *       supported" for Ed25519, so the key is silently dropped and the token is reported as
 *       having "no matching key(s)".</li>
 * </ol>
 *
 * <p>Neither is a configuration problem, so the verification is assembled from the APIs Nimbus
 * does document for Ed25519: keys are discovered and cached through {@link RemoteJWKSet} (which
 * also re-fetches on an unfamiliar {@code kid}, giving key rotation for free), matched by the
 * JWS header, and verified with {@link Ed25519Verifier}. Spring still owns claim conversion and
 * validation, so expiry, issuer and audience rules behave exactly as they would with the
 * built-in decoder.
 *
 * <p>Only EdDSA is accepted. An unsigned token, an {@code HS256} token, or anything else is
 * rejected before a key is even looked up, which rules out signature-confusion attacks.
 */
@Slf4j
public class NeonAuthJwtDecoder implements JwtDecoder {


    /** Same conversion the built-in decoder applies: numeric dates become Instants, etc. */
    private static final Converter<Map<String, Object>, Map<String, Object>> CLAIM_CONVERTER =
            MappedJwtClaimSetConverter.withDefaults(Collections.emptyMap());

    private final JWKSource<SecurityContext> jwkSource;
    private final OAuth2TokenValidator<Jwt> validator;

    public NeonAuthJwtDecoder(URL jwksUri, OAuth2TokenValidator<Jwt> validator) {
        this.jwkSource = new RemoteJWKSet<>(jwksUri);
        this.validator = validator;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        SignedJWT signedJwt = parse(token);
        JWK key = resolveKey(signedJwt);
        verifySignature(signedJwt, key);
        Jwt jwt = toSpringJwt(token, signedJwt);
        validate(jwt);
        return jwt;
    }

    private SignedJWT parse(String token) {
        JWT parsed;
        try {
            parsed = JWTParser.parse(token);
        } catch (ParseException ex) {
            throw new BadJwtException("Failed to parse the JWT: " + ex.getMessage(), ex);
        }
        if (!(parsed instanceof SignedJWT signedJwt)) {
            throw new BadJwtException("Unsupported JWT: only signed tokens are accepted");
        }
        // Pinning the algorithm is what stops an attacker re-signing the payload some other way.
        JWSAlgorithm algorithm = signedJwt.getHeader().getAlgorithm();
        if (!JWSAlgorithm.EdDSA.equals(algorithm)) {
            throw new BadJwtException("Unsupported algorithm: expected EdDSA but found " + algorithm);
        }
        return signedJwt;
    }

    /** Finds the published key for this token, re-fetching the JWK set when the kid is new. */
    private JWK resolveKey(SignedJWT signedJwt) {
        List<JWK> matches;
        try {
            matches = jwkSource.get(
                    new JWKSelector(JWKMatcher.forJWSHeader(signedJwt.getHeader())), null);
        } catch (Exception ex) {
            // The JWK set could not be retrieved (network, or a malformed document).
            log.warn("Unable to retrieve Neon Auth's JWK set: {}", ex.getMessage());
            throw new JwtException("Unable to verify the token: the signing keys could not be retrieved", ex);
        }
        if (matches == null || matches.isEmpty()) {
            throw new BadJwtException(
                    "No Neon Auth key matches this token (kid=" + signedJwt.getHeader().getKeyID() + ")");
        }
        return matches.getFirst();
    }

    private void verifySignature(SignedJWT signedJwt, JWK key) {
        if (!(key instanceof OctetKeyPair octetKeyPair)) {
            throw new BadJwtException("Neon Auth key of type " + key.getKeyType() + " cannot verify EdDSA");
        }
        try {
            JWSVerifier verifier = new Ed25519Verifier(octetKeyPair);
            if (!signedJwt.verify(verifier)) {
                throw new BadJwtException("JWT signature verification failed");
            }
        } catch (JOSEException ex) {
            throw new BadJwtException("JWT signature verification failed: " + ex.getMessage(), ex);
        }
    }

    private Jwt toSpringJwt(String token, SignedJWT signedJwt) {
        Map<String, Object> headers = signedJwt.getHeader().toJSONObject();
        Map<String, Object> rawClaims;
        try {
            rawClaims = signedJwt.getJWTClaimsSet().getClaims();
        } catch (ParseException ex) {
            throw new BadJwtException("Failed to read the JWT claims: " + ex.getMessage(), ex);
        }
        Map<String, Object> claims = CLAIM_CONVERTER.convert(rawClaims);
        try {
            return Jwt.withTokenValue(token)
                    .headers(header -> header.putAll(headers))
                    .claims(claim -> claim.putAll(claims))
                    .build();
        } catch (IllegalArgumentException ex) {
            // Spring rejects a claim set whose expiry precedes its issue time at construction.
            // Report it as a bad token rather than letting the failure escape as a runtime error.
            throw new BadJwtException("Invalid JWT claims: " + ex.getMessage(), ex);
        }
    }

    private void validate(Jwt jwt) {
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        if (result.hasErrors()) {
            List<String> descriptions = new ArrayList<>();
            for (OAuth2Error error : result.getErrors()) {
                descriptions.add(error.getDescription() != null ? error.getDescription() : error.getErrorCode());
            }
            // The claim name is included so a rejected token is diagnosable from the logs alone.
            throw new JwtValidationException(
                    "An error occurred while attempting to decode the Jwt: "
                            + String.join(", ", descriptions), result.getErrors());
        }
    }
}
