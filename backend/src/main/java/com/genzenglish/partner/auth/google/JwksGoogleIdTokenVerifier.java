package com.genzenglish.partner.auth.google;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.genzenglish.partner.common.ApiException;
import com.genzenglish.partner.config.AppProperties;

/** Verifies Google ID tokens against Google's published signing keys, issuer and our OAuth client IDs. */
@Component
@ConditionalOnProperty(prefix = "app.auth.google", name = "provider", havingValue = "google")
public class JwksGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final NimbusJwtDecoder decoder;

    public JwksGoogleIdTokenVerifier(AppProperties props) {
        List<String> clientIds = props.auth().google().clientIds();
        if (clientIds.isEmpty()) {
            throw new IllegalStateException(
                    "Set app.auth.google.client-ids (GOOGLE_CLIENT_IDS) when app.auth.google.provider=google");
        }
        this.decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> GOOGLE_ISSUERS.contains(String.valueOf(iss))),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.stream().anyMatch(clientIds::contains))));
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw ApiException.unauthorized("google_token_invalid",
                    "Google sign-in didn't go through. Try again, or use email instead.");
        }
        String email = jwt.getClaimAsString("email");
        if (email == null) {
            throw ApiException.unauthorized("google_email_missing",
                    "Your Google account didn't share an email. Try another account or use email sign-up.");
        }
        Object verified = jwt.getClaim("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equalsIgnoreCase(String.valueOf(verified));
        return new GoogleIdentity(jwt.getSubject(), email, emailVerified, jwt.getClaimAsString("name"));
    }
}
