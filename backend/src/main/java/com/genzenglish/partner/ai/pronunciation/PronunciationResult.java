package com.genzenglish.partner.ai.pronunciation;

import java.util.List;

/**
 * All scores are 0..100.
 */
public record PronunciationResult(int overall, int accuracy, int fluency, int completeness, List<WordScore> words) {

    public PronunciationResult {
        words = List.copyOf(words);
    }

    public record WordScore(String word, int score, ErrorType error, List<PhonemeScore> phonemes) {
        public WordScore {
            phonemes = List.copyOf(phonemes);
        }
    }

    public record PhonemeScore(String phoneme, int score) {
    }

    public enum ErrorType {
        NONE,
        MISPRONUNCIATION,
        OMISSION,
        INSERTION
    }
}
