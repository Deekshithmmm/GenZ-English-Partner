package com.genzenglish.partner.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Everything the app reads from configuration under the {@code app.*} prefix.
 * Provider fields pick an implementation at startup; "mock" keeps the whole stack runnable without API keys.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Auth auth,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Ai ai,
        @Valid @NotNull Privacy privacy) {

    public record Jwt(
            @NotBlank @Size(min = 32, message = "must be at least 32 characters") String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {
    }

    public record Auth(@Valid @NotNull Google google) {
        /**
         * @param provider  "google" verifies real Google ID tokens; "mock" accepts {@code mock-google:<email>} for local dev.
         * @param clientIds OAuth client IDs whose tokens we accept (web client ID used by the Android app, plus iOS).
         */
        public record Google(@NotBlank String provider, List<String> clientIds) {
            public Google {
                clientIds = clientIds == null ? List.of() : List.copyOf(clientIds);
            }
        }
    }

    /**
     * @param allowedOrigins origin patterns a browser app may call the API from
     * @param allowLan       also let in pages served from a private network address, like a phone on the laptop's
     *                       Wi-Fi or hotspot opening the web app at the laptop's address (development only)
     */
    public record Cors(List<String> allowedOrigins, boolean allowLan) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    public record Ai(
            @Valid @NotNull Provider llm,
            @Valid @NotNull Provider stt,
            @Valid @NotNull Provider tts,
            @Valid @NotNull Provider pronunciation) {

        public record Provider(@NotBlank String provider) {
        }
    }

    /**
     * @param keepAudioByDefault raw learner audio is deleted after scoring unless the learner opts in.
     */
    public record Privacy(boolean keepAudioByDefault) {
    }
}
