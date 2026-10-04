package com.genzenglish.partner.practice;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.genzenglish.partner.ai.AudioClip;
import com.genzenglish.partner.ai.pronunciation.PronunciationRequest;
import com.genzenglish.partner.ai.pronunciation.PronunciationResult;
import com.genzenglish.partner.ai.pronunciation.PronunciationScorer;
import com.genzenglish.partner.practice.TurnFeedback.SoundSlip;

/**
 * Scores a spoken answer against its own transcript, so the feedback can name the one or two words that didn't come
 * through clearly. Only content words are flagged: short function words are naturally reduced in fluent speech, and
 * a note about "the" on every answer would be noise. Scoring problems never block the answer.
 */
@Component
class SoundCheck {

    static final int UNCLEAR_BELOW = 60;
    static final int MAX_SLIPS = 2;
    static final String LANGUAGE_TAG = "en-IN";
    private static final Set<String> FUNCTION_WORDS = Set.of("that", "this", "with", "have", "what", "which",
            "when", "where", "there", "their", "they", "them", "then", "than", "were", "would", "should", "could",
            "will", "your", "from", "into", "just", "also", "very", "some", "because", "about", "been", "being",
            "these", "those", "while", "whose", "whom", "here");
    private static final Logger log = LoggerFactory.getLogger(SoundCheck.class);

    private final PronunciationScorer scorer;

    SoundCheck(PronunciationScorer scorer) {
        this.scorer = scorer;
    }

    /**
     * @param overall 0..100 for the whole answer, or null when it couldn't be scored
     */
    record Result(Integer overall, List<SoundSlip> slips) {

        Result {
            slips = slips == null ? List.of() : List.copyOf(slips);
        }

        static Result none() {
            return new Result(null, List.of());
        }
    }

    Result check(AudioClip audio, String transcript) {
        if (audio == null || transcript == null || transcript.isBlank()) {
            return Result.none();
        }
        try {
            PronunciationResult result = scorer.score(new PronunciationRequest(audio, transcript, LANGUAGE_TAG));
            return new Result(Math.clamp(result.overall(), 0, 100), unclearWords(result.words()));
        } catch (RuntimeException e) {
            log.warn("Pronunciation scoring failed ({}); skipping the sound check", scorer.providerName(), e);
            return Result.none();
        }
    }

    /** The lowest-scoring content words under the threshold, each word once. */
    static List<SoundSlip> unclearWords(List<PronunciationResult.WordScore> words) {
        Map<String, Integer> lowest = new LinkedHashMap<>();
        for (PronunciationResult.WordScore word : words) {
            String text = word.word() == null ? "" : TurnFeedback.normalize(word.word());
            if (text.length() < 4 || text.contains(" ") || FUNCTION_WORDS.contains(text)
                    || word.error() == PronunciationResult.ErrorType.OMISSION
                    || word.error() == PronunciationResult.ErrorType.INSERTION || word.score() >= UNCLEAR_BELOW) {
                continue;
            }
            lowest.merge(text, word.score(), Math::min);
        }
        return lowest.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .limit(MAX_SLIPS)
                .map(e -> new SoundSlip(e.getKey(), e.getValue()))
                .toList();
    }
}
