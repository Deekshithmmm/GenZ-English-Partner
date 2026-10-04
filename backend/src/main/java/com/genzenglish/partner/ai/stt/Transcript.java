package com.genzenglish.partner.ai.stt;

import java.util.List;

/**
 * @param confidence 0..1 for the whole utterance
 * @param words      per-word timings when the provider returns them (used for pace and pause analysis)
 */
public record Transcript(String text, double confidence, List<Word> words) {

    public Transcript {
        text = text == null ? "" : text.strip();
        words = words == null ? List.of() : List.copyOf(words);
    }

    public boolean isBlank() {
        return text.isBlank();
    }

    public record Word(String text, long startMs, long endMs, double confidence) {
    }
}
