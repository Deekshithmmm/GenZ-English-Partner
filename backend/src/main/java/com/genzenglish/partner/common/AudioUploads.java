package com.genzenglish.partner.common;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import com.genzenglish.partner.ai.AudioClip;

/** Checks a recorded answer from the app before anything is sent to speech-to-text. */
public final class AudioUploads {

    public static final long MIN_AUDIO_MS = 500;
    public static final long MAX_AUDIO_MS = 180_000;

    private AudioUploads() {
    }

    public static AudioClip read(MultipartFile audio, long durationMs) throws IOException {
        if (audio.isEmpty() || durationMs < MIN_AUDIO_MS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "too_short",
                    "That was too quick to hear. Hold the mic the whole time you talk.");
        }
        if (durationMs > MAX_AUDIO_MS) {
            throw ApiException.badRequest("too_long", "Keep each answer under 3 minutes.");
        }
        String contentType = audio.getContentType() == null ? "application/octet-stream" : audio.getContentType();
        return new AudioClip(audio.getBytes(), contentType, durationMs);
    }
}
