package com.genzenglish.partner.auth;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.genzenglish.partner.user.MeResponse;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Enter your email.") @Email(message = "That email doesn't look right. Check it and try again.") @Size(max = 320) String email,
            @NotBlank(message = "Choose a password.") @Size(min = 8, max = 128, message = "Use at least 8 characters for your password.") String password,
            @NotBlank(message = "Tell us what to call you.") @Size(max = 80, message = "Keep your name under 80 characters.") String displayName) {
    }

    public record LoginRequest(
            @NotBlank(message = "Enter your email.") String email,
            @NotBlank(message = "Enter your password.") String password) {
    }

    public record GoogleSignInRequest(@NotBlank(message = "Google sign-in didn't return a token. Try again.") String idToken) {
    }

    public record RefreshRequest(@NotBlank(message = "Sign in again.") String refreshToken) {
    }

    public record AuthResponse(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            MeResponse me) {

        static AuthResponse of(TokenService.IssuedTokens tokens, MeResponse me) {
            return new AuthResponse(tokens.accessToken(), tokens.accessTokenExpiresAt(), tokens.refreshToken(),
                    tokens.refreshTokenExpiresAt(), me);
        }
    }

    /** Tells the app which sign-in options to show. {@code google} is "google", "mock" or "disabled". */
    public record ProvidersResponse(boolean email, String google) {
    }
}
