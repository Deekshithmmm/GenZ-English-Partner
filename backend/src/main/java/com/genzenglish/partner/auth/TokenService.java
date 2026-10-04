package com.genzenglish.partner.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genzenglish.partner.common.ApiException;
import com.genzenglish.partner.config.AppProperties;
import com.genzenglish.partner.user.User;
import com.genzenglish.partner.user.UserRepository;

@Service
public class TokenService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final AppProperties.Jwt jwtProps;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens, UserRepository users,
            AppProperties props, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.jwtProps = props.jwt();
        this.clock = clock;
    }

    public record IssuedTokens(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
            Instant refreshTokenExpiresAt) {
    }

    /** Starts a new token family, e.g. on sign-in or sign-up. */
    @Transactional
    public IssuedTokens issue(User user) {
        return issueInFamily(user, UUID.randomUUID());
    }

    /**
     * Exchanges a refresh token for a new pair. A token can be used once; presenting a revoked token means it leaked
     * or was replayed, so the whole family is revoked and the learner has to sign in again.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotated rotate(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(TokenService::sessionEnded);
        if (current.isRevoked()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw sessionEnded();
        }
        if (current.isExpired(now)) {
            throw sessionEnded();
        }
        current.revoke(now);
        User user = users.findById(current.getUserId()).orElseThrow(TokenService::sessionEnded);
        return new Rotated(user, issueInFamily(user, current.getFamilyId()));
    }

    public record Rotated(User user, IssuedTokens tokens) {
    }

    private IssuedTokens issueInFamily(User user, UUID familyId) {
        Instant now = clock.instant();
        Instant accessExpires = now.plus(jwtProps.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProps.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(accessExpires)
                .claim("email", user.getEmail())
                .build();
        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        String rawRefresh = newOpaqueToken();
        Instant refreshExpires = now.plus(jwtProps.refreshTokenTtl());
        refreshTokens.save(new RefreshToken(user.getId(), familyId, hash(rawRefresh), now, refreshExpires));
        return new IssuedTokens(accessToken, accessExpires, rawRefresh, refreshExpires);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokens.revokeAllForUser(userId, clock.instant());
    }

    private String newOpaqueToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    static String hash(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static ApiException sessionEnded() {
        return ApiException.unauthorized("session_expired", "Your session has ended. Sign in again.");
    }
}
