package com.genzenglish.partner.speech;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Holds synthesized tutor audio briefly so the app can fetch it by an unguessable ID. Luna's audio is not personal
 * data, and clips expire after a few minutes. A clip can also be fetched while it's still being made
 * ({@link LiveClip}), so Luna starts talking as soon as the first audio arrives.
 */
@Component
public class SpeechClipStore {

    static final Duration TTL = Duration.ofMinutes(10);
    static final int MAX_CLIPS = 2_000;

    private final Map<String, Clip> clips = new ConcurrentHashMap<>();
    private final Map<String, LiveClip> making = new ConcurrentHashMap<>();
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
        String id = newId();
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

    /** Starts a clip whose audio is still being made; add its audio as it arrives, then finish (or fail) it. */
    public LiveClip open(String contentType) {
        LiveClip clip = new LiveClip(newId(), contentType);
        making.put(clip.id(), clip);
        return clip;
    }

    /** A clip still being made, while it is. Once finished it's an ordinary clip ({@link #get}). */
    public Optional<LiveClip> live(String id) {
        return Optional.ofNullable(making.get(id));
    }

    private String newId() {
        byte[] idBytes = new byte[18];
        random.nextBytes(idBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(idBytes);
    }

    @Scheduled(fixedDelay = 60_000)
    void evictExpired() {
        Instant now = clock.instant();
        clips.values().removeIf(clip -> clip.expiresAt().isBefore(now));
    }

    /**
     * Audio arriving piece by piece. Readers get each piece as soon as it's added and wait for more until the clip is
     * finished; then it's kept like any other clip, so it can be played again.
     */
    public final class LiveClip {

        private final String id;
        private final String contentType;
        private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition changed = lock.newCondition();
        private boolean finished;
        private boolean failed;

        private LiveClip(String id, String contentType) {
            this.id = id;
            this.contentType = contentType;
        }

        public String id() {
            return id;
        }

        public String contentType() {
            return contentType;
        }

        public void add(byte[] piece) {
            if (piece.length == 0) {
                return;
            }
            lock.lock();
            try {
                audio.writeBytes(piece);
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }

        /** All the audio is in: from now on it's an ordinary clip. */
        public void finish() {
            lock.lock();
            try {
                finished = true;
                clips.put(id, new Clip(audio.toByteArray(), contentType, clock.instant().plus(TTL)));
                changed.signalAll();
            } finally {
                lock.unlock();
            }
            making.remove(id, this);
        }

        /** The voice failed: readers stop, and a reader that hasn't had any audio gets an error to fall back on. */
        public void fail() {
            lock.lock();
            try {
                failed = true;
                finished = true;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
            making.remove(id, this);
        }

        /** Waits until the first audio is in; false if the voice failed first or nothing came in time. */
        public boolean awaitStart(Duration wait) throws InterruptedException {
            long nanos = wait.toNanos();
            lock.lock();
            try {
                while (audio.size() == 0 && !finished && nanos > 0) {
                    nanos = changed.awaitNanos(nanos);
                }
                return audio.size() > 0;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Writes the audio to {@code out} as it arrives, until the clip is finished, failed, or nothing new has come for
         * {@code idle}.
         */
        public void copyTo(OutputStream out, Duration idle) throws IOException, InterruptedException {
            int sent = 0;
            while (true) {
                byte[] next;
                boolean done;
                lock.lock();
                try {
                    long nanos = idle.toNanos();
                    while (audio.size() == sent && !finished && nanos > 0) {
                        nanos = changed.awaitNanos(nanos);
                    }
                    byte[] all = audio.toByteArray();
                    next = Arrays.copyOfRange(all, sent, all.length);
                    done = finished || nanos <= 0;
                } finally {
                    lock.unlock();
                }
                if (next.length > 0) {
                    out.write(next);
                    out.flush();
                    sent += next.length;
                }
                if (done && next.length == 0) {
                    return;
                }
            }
        }

        /** For tests: waits until the clip is finished or failed. */
        boolean awaitDone(Duration wait) throws InterruptedException {
            long nanos = wait.toNanos();
            lock.lock();
            try {
                while (!finished && nanos > 0) {
                    nanos = changed.awaitNanos(nanos);
                }
                return finished;
            } finally {
                lock.unlock();
            }
        }

        boolean failed() {
            lock.lock();
            try {
                return failed;
            } finally {
                lock.unlock();
            }
        }
    }
}
