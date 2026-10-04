package com.genzenglish.partner.auth;

import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genzenglish.partner.auth.AuthDtos.AuthResponse;
import com.genzenglish.partner.auth.AuthDtos.LoginRequest;
import com.genzenglish.partner.auth.AuthDtos.RegisterRequest;
import com.genzenglish.partner.auth.google.GoogleIdTokenVerifier;
import com.genzenglish.partner.auth.google.GoogleIdentity;
import com.genzenglish.partner.common.ApiException;
import com.genzenglish.partner.profile.LearnerProfile;
import com.genzenglish.partner.profile.LearnerProfileRepository;
import com.genzenglish.partner.profile.ProfileService;
import com.genzenglish.partner.user.MeResponse;
import com.genzenglish.partner.user.User;
import com.genzenglish.partner.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final LearnerProfileRepository profileRepository;
    private final ProfileService profiles;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final GoogleIdTokenVerifier googleVerifier;
    // Checked when the email is unknown, so a missing account costs the same time as a wrong password.
    private final String timingDummyHash;

    public AuthService(UserRepository users, LearnerProfileRepository profileRepository, ProfileService profiles,
            PasswordEncoder passwordEncoder, TokenService tokens, GoogleIdTokenVerifier googleVerifier) {
        this.users = users;
        this.profileRepository = profileRepository;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.googleVerifier = googleVerifier;
        this.timingDummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        User user;
        try {
            user = users.saveAndFlush(User.withPassword(email, request.displayName().strip(),
                    passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
        LearnerProfile profile = profiles.createFor(user.getId());
        return AuthResponse.of(tokens.issue(user), MeResponse.of(user, profile));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalizeEmail(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), timingDummyHash);
            throw invalidCredentials();
        }
        if (user.getPasswordHash() == null) {
            throw ApiException.unauthorized("use_google",
                    "This email signs in with Google. Tap \"Continue with Google\" instead.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return AuthResponse.of(tokens.issue(user), MeResponse.of(user, profileOf(user)));
    }

    /**
     * Signs in with a verified Google identity. Links to an existing email account when Google has verified that
     * email; otherwise creates a new account.
     */
    @Transactional
    public AuthResponse google(String idToken) {
        GoogleIdentity identity = googleVerifier.verify(idToken);
        User user = users.findByGoogleSubject(identity.subject()).orElse(null);
        if (user == null) {
            String email = normalizeEmail(identity.email());
            user = users.findByEmail(email).orElse(null);
            if (user != null) {
                if (!identity.emailVerified()) {
                    throw ApiException.conflict("google_email_unverified",
                            "An account with this email already exists. Sign in with your password.");
                }
                user.linkGoogle(identity.subject());
            } else {
                String name = identity.name() == null || identity.name().isBlank()
                        ? email.substring(0, email.indexOf('@'))
                        : identity.name().strip();
                user = users.saveAndFlush(User.withGoogle(email, truncate(name, 80), identity.subject()));
                profiles.createFor(user.getId());
            }
        }
        return AuthResponse.of(tokens.issue(user), MeResponse.of(user, profileOf(user)));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String refreshToken) {
        TokenService.Rotated rotated = tokens.rotate(refreshToken);
        return AuthResponse.of(rotated.tokens(), MeResponse.of(rotated.user(), profileOf(rotated.user())));
    }

    @Transactional
    public void logout(String refreshToken) {
        tokens.revoke(refreshToken);
    }

    private LearnerProfile profileOf(User user) {
        return profileRepository.findById(user.getId()).orElseGet(() -> profiles.createFor(user.getId()));
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static ApiException emailTaken() {
        return ApiException.conflict("email_taken", "An account with this email already exists. Sign in instead.");
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("invalid_credentials",
                "That email and password don't match. Check them and try again.");
    }
}
