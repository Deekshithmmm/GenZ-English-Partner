package com.genzenglish.partner.ai.pronunciation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.genzenglish.partner.ai.pronunciation.PronunciationResult.ErrorType;
import com.genzenglish.partner.ai.pronunciation.PronunciationResult.PhonemeScore;
import com.genzenglish.partner.ai.pronunciation.PronunciationResult.WordScore;

/**
 * Deterministic scores for local development. Words containing common Indian-English trouble sounds (v/w, th, z,
 * sh) score lower so the lab's colour coding and focus packs have something realistic to show.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai.pronunciation", name = "provider", havingValue = "mock",
        matchIfMissing = true)
public class MockPronunciationScorer implements PronunciationScorer {

    private static final List<String> TROUBLE = List.of("th", "v", "w", "z", "sh", "j");
    private static final List<String> DIGRAPHS = List.of("th", "sh", "ch", "ng", "ph", "wh", "ee", "oo");

    @Override
    public PronunciationResult score(PronunciationRequest request) {
        String[] words = request.referenceText().toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").trim()
                .split("\\s+");
        long seed = request.audio().bytes().length + request.audio().durationMs();
        List<WordScore> scores = new ArrayList<>();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            int base = 78 + (int) Math.floorMod(seed + word.hashCode(), 20L);
            boolean trouble = TROUBLE.stream().anyMatch(word::contains);
            int score = trouble ? base - 25 : base;
            List<PhonemeScore> phonemes = phonemes(word, score);
            scores.add(new WordScore(word, score, score < 60 ? ErrorType.MISPRONUNCIATION : ErrorType.NONE, phonemes));
        }
        int accuracy = (int) Math.round(scores.stream().mapToInt(WordScore::score).average().orElse(0));
        int fluency = Math.min(100, 70 + (int) Math.floorMod(seed, 25L));
        int overall = Math.round(accuracy * 0.6f + fluency * 0.3f + 100 * 0.1f);
        return new PronunciationResult(overall, accuracy, fluency, 100, scores);
    }

    @Override
    public String providerName() {
        return "mock";
    }

    private static List<PhonemeScore> phonemes(String word, int wordScore) {
        List<PhonemeScore> result = new ArrayList<>();
        int i = 0;
        while (i < word.length()) {
            String unit = word.substring(i, Math.min(i + 2, word.length()));
            String phoneme = unit.length() == 2 && DIGRAPHS.contains(unit) ? unit : word.substring(i, i + 1);
            boolean trouble = TROUBLE.contains(phoneme);
            result.add(new PhonemeScore(phoneme, trouble ? Math.max(20, wordScore - 20) : Math.min(100, wordScore + 8)));
            i += phoneme.length();
        }
        return result;
    }
}
