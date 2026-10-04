package com.genzenglish.partner.practice;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.genzenglish.partner.practice.PracticeDtos.AnswerResponse;
import com.genzenglish.partner.practice.PracticeDtos.ProblemEvent;
import com.genzenglish.partner.practice.PracticeDtos.ReplyEvent;
import com.genzenglish.partner.practice.PracticeDtos.TranscriptEvent;

/**
 * The server-sent events for one answer. If the app goes away mid-turn, sends are skipped but the work carries on, so
 * the answer is still saved and a retry gets it back.
 */
final class TurnEvents {

    static final long TIMEOUT_MS = 90_000;
    /** Luna's words so far are sent at most this often, or whenever this many new characters have arrived. */
    static final long REPLY_EVERY_MS = 80;
    static final int REPLY_EVERY_CHARS = 40;

    private final SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
    private volatile boolean open = true;
    private long lastReplyAtMs;
    private int lastReplyLength;

    TurnEvents() {
        emitter.onCompletion(() -> open = false);
        emitter.onTimeout(() -> open = false);
        emitter.onError(e -> open = false);
    }

    SseEmitter emitter() {
        return emitter;
    }

    void transcript(int turnNumber, String transcript, Integer wordsPerMinute, int fillerCount) {
        send("transcript", new TranscriptEvent(turnNumber, transcript, wordsPerMinute, fillerCount));
    }

    /** Luna's cleaned-up words so far. Throttled, since the finished line follows in the {@code kai} event. */
    void reply(String soFar) {
        long now = System.nanoTime() / 1_000_000;
        if (now - lastReplyAtMs >= REPLY_EVERY_MS || soFar.length() - lastReplyLength >= REPLY_EVERY_CHARS) {
            lastReplyAtMs = now;
            lastReplyLength = soFar.length();
            send("reply", new ReplyEvent(soFar));
        }
    }

    void send(String name, Object data) {
        if (!open) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // The app disconnected or the stream timed out; keep working so the answer is saved.
            open = false;
        }
    }

    void problem(String code, String message) {
        send("problem", new ProblemEvent(code, message));
    }

    /** An answer that was already stored: everything is known, so it goes out at once. */
    void replay(AnswerResponse stored) {
        transcript(stored.turnNumber(), stored.transcript(), stored.wordsPerMinute(), stored.fillerCount());
        send("kai", stored.kai());
        send("feedback", stored.feedback());
        send("done", stored);
        complete();
    }

    void complete() {
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            // Already completed or timed out.
        }
    }
}
