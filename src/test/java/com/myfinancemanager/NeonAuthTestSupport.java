package com.myfinancemanager;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * A stand-in for the Neon Auth service.
 *
 * <p>Rather than mocking the verification path, this serves a real JWK set over HTTP and signs
 * real Ed25519 tokens, so the production {@code NeonAuthConfig} decoder is exercised end to end:
 * key discovery, EdDSA verification, and the issuer/audience/expiry validators. The signing
 * algorithm is the whole reason this matters — Neon Auth uses EdDSA, which Spring Security 6.3
 * cannot express through its fluent builder, so a passing test here proves the hand-built
 * processor actually verifies Neon Auth's signatures.
 *
 * <p>The HTTP server binds an ephemeral loopback port, so nothing external is contacted.
 */
public final class NeonAuthTestSupport {

    static final String KEY_ID = "test-key";

    private static final OctetKeyPair KEY;
    private static final OctetKeyPair OTHER_KEY;
    private static final JWSSigner SIGNER;
    private static final JWSSigner OTHER_SIGNER;
    private static final HttpServer SERVER;
    private static final String ORIGIN;

    static {
        try {
            KEY = generateKey(KEY_ID);
            OTHER_KEY = generateKey("other-key");
            SIGNER = new Ed25519Signer(KEY);
            OTHER_SIGNER = new Ed25519Signer(OTHER_KEY);

            String jwks = new JWKSet(KEY.toPublicJWK()).toString();
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.createContext("/", exchange -> {
                byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            SERVER.start();
            ORIGIN = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private NeonAuthTestSupport() {
    }

    /** Base URL in Neon Auth's shape: {@code <origin>/<database>/auth}. */
    public static String baseUrl() {
        return ORIGIN + "/testdb/auth";
    }

    /** The origin, which is what Neon Auth puts in the iss and aud claims. */
    public static String issuer() {
        return ORIGIN;
    }

    /** A valid token for a brand new identity. */
    public static String token(String subject, String email) {
        return token(subject, email, "Test User", issuer(), issuer(), SIGNER,
                Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    /** A token whose audience names some other service, so validation must reject it. */
    public static String tokenWithWrongAudience(String subject, String email) {
        return token(subject, email, "Test User", issuer(), "https://somewhere-else.invalid",
                SIGNER, Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    /** A token signed by a key that is absent from the published JWK set. */
    public static String tokenSignedByUnknownKey(String subject, String email) {
        return token(subject, email, "Test User", issuer(), issuer(), OTHER_SIGNER,
                Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    /**
     * A well-formed token that has already expired.
     *
     * <p>The issued-at claim is moved back with it, because that is the shape Neon Auth
     * actually produces: an expired token is simply one issued long enough ago, never one whose
     * expiry precedes its issue time.
     */
    public static String expiredToken(String subject, String email) {
        Instant issuedAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        return token(subject, email, "Test User", issuer(), issuer(), SIGNER,
                issuedAt, issuedAt.plus(15, ChronoUnit.MINUTES));
    }

    private static String token(String subject, String email, String name, String issuer,
                                String audience, JWSSigner signer, Instant now, Instant expiresAt) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("email", email)
                .claim("name", name)
                .claim("emailVerified", false)
                .claim("role", "authenticated");
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KEY_ID).build(),
                    claims.build());
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to mint a test token", ex);
        }
    }

    private static OctetKeyPair generateKey(String keyId) throws Exception {
        return new OctetKeyPairGenerator(Curve.Ed25519)
                .keyID(keyId)
                .algorithm(JWSAlgorithm.EdDSA)
                .generate();
    }
}
