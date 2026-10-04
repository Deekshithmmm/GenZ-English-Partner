package com.genzenglish.partner.ai;

/**
 * Learner audio held in memory only for as long as scoring needs it.
 *
 * @param contentType e.g. {@code audio/mp4} (Android/iOS AAC) or {@code audio/webm} (web)
 * @param durationMs  as measured by the recorder on the device
 */
public record AudioClip(byte[] bytes, String contentType, long durationMs) {

    public AudioClip {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("audio is empty");
        }
    }
}
