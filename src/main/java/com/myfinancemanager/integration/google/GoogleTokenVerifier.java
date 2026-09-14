package com.myfinancemanager.integration.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.myfinancemanager.common.exception.ServiceUnavailableException;
import com.myfinancemanager.common.exception.UnauthorizedException;
import com.myfinancemanager.config.GoogleProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class GoogleTokenVerifier {

    private final GoogleProperties properties;
    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(GoogleProperties properties) {
        this.properties = properties;
        this.verifier = properties.isConfigured()
                ? new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(properties.clientId()))
                        .build()
                : null;
    }

    public GoogleAccount verify(String idToken) {
        if (!properties.isConfigured()) {
            throw new ServiceUnavailableException("Google Sign-In is not configured on this server");
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new UnauthorizedException("Invalid Google ID token");
            }
            GoogleIdToken.Payload payload = token.getPayload();
            return new GoogleAccount(
                    payload.getSubject(),
                    payload.getEmail(),
                    (String) payload.get("name"),
                    Boolean.TRUE.equals(payload.getEmailVerified()));
        } catch (UnauthorizedException | ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Unable to verify Google ID token");
        }
    }
}
