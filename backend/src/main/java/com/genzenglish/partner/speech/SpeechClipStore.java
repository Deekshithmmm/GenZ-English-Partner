package com.genzenglish.partner.speech;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Holds synthesized tutor audio briefly so the app can fetch it by an unguessable ID. Luna's audio is not personal
 * data, and clips expire after a few minutes.
 */
@Component
public class SpeechClipStore {

    static final Duration TTL = Duration.ofMinutes(10);
    static final int MAX_CLIPS = 2_000;

    private final Map<String, Clip> clips = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public SpeechClipStore(Clock clock) {
        this.clock = clock;
    }

    public record Clip(byte[] audio, String contentType, Instant expiresAt) {
    }

    public String put(byte[] audio, String contentType) {
        if (clips.size() >= MAX_CLIPS) {
            evictExpired();
        }
        byte[] idBytes = new byte[18];
        random.nextBytes(idBytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(idBytes);
        clips.put(id, new Clip(audio, contentType, clock.instant().plus(TTL)));
        return id;
    }

    public Optional<Clip> get(String id) {
        Clip clip = clips.get(id);
        if (clip == null || clip.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(clip);
    }

    @Scheduled(fixedDelay = 60_000)
    void evictExpired() {
        Instant now = clock.instant();
        clips.values().removeIf(clip -> clip.expiresAt().isBefore(now));
    }
}
