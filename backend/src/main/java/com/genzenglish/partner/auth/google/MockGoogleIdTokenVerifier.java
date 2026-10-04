package com.genzenglish.partner.auth.google;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.genzenglish.partner.common.ApiException;

/**
 * Local-development stand-in for Google Sign-In. Accepts {@code mock-google:<email>} or
 * {@code mock-google:<email>:<name>}. Never enable outside local development.
 */
@Component
@ConditionalOnProperty(prefix = "app.auth.google", name = "provider", havingValue = "mock")
public class MockGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    public static final String PREFIX = "mock-google:";
    private static final Logger log = LoggerFactory.getLogger(MockGoogleIdTokenVerifier.class);

    public MockGoogleIdTokenVerifier() {
        log.warn("Google sign-in is MOCKED (app.auth.google.provider=mock). Do not use this in production.");
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (idToken == null || !idToken.startsWith(PREFIX)) {
            throw ApiException.unauthorized("google_token_invalid",
                    "Google sign-in didn't go through. Try again, or use email instead.");
        }
        String[] parts = idToken.substring(PREFIX.length()).split(":", 2);
        String email = parts[0].trim().toLowerCase();
        if (!email.contains("@")) {
            throw ApiException.unauthorized("google_token_invalid",
                    "Google sign-in didn't go through. Try again, or use email instead.");
        }
        String name = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : email.substring(0, email.indexOf('@'));
        String subject = "mock-" + UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8));
        return new GoogleIdentity(subject, email, true, name);
    }
}
