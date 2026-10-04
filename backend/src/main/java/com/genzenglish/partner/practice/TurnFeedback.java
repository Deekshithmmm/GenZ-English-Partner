package com.genzenglish.partner.practice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.genzenglish.partner.profile.ExplanationLanguage;

/**
 * Luna's notes on one answer, shown under the learner's words and never spoken mid-conversation: at most two
 * corrections that matter, a smoother way to say the whole answer, a stronger word to try, the filler words heard and
 * words that didn't come through clearly.
 *
 * @param tip            one short pointer, or null
 * @param betterPhrasing the whole answer said more naturally, or null when it was already fine
 * @param newWords       at most one or two words worth adding to the learner's vocabulary
 * @param fillerWords    counted from the transcript, not by the model
 * @param mispronounced  from the pronunciation scorer, for spoken answers only
 */
public record TurnFeedback(List<Correction> corrections, String tip, String betterPhrasing, List<NewWord> newWords,
        List<FillerWord> fillerWords, List<SoundSlip> mispronounced) {

    static final int MAX_CORRECTIONS = 2;
    static final int MAX_NEW_WORDS = 2;
    private static final int MAX_ORIGINAL = 120;
    private static final int MAX_BETTER = 160;
    private static final int MAX_WHY = 200;
    private static final int MAX_WHY_NATIVE = 320;
    private static final int MAX_TIP = 160;
    private static final int MAX_BETTER_PHRASING = 400;
    private static final int MAX_WORD = 40;
    private static final int MAX_MEANING = 120;

    public TurnFeedback {
        corrections = corrections == null ? List.of() : List.copyOf(corrections);
        newWords = newWords == null ? List.of() : List.copyOf(newWords);
        fillerWords = fillerWords == null ? List.of() : List.copyOf(fillerWords);
        mispronounced = mispronounced == null ? List.of() : List.copyOf(mispronounced);
    }

    public TurnFeedback(List<Correction> corrections, String tip) {
        this(corrections, tip, null, List.of(), List.of(), List.of());
    }

    /**
     * @param original  the learner's own words, exactly as transcribed
     * @param better    a natural way to say it
     * @param why       one short, friendly reason, or null
     * @param whyNative the same reason in the learner's explanation language, or null when that's English
     */
    public record Correction(String original, String better, String why, String whyNative) {

        public Correction(String original, String better, String why) {
            this(original, better, why, null);
        }
    }

    /**
     * @param word    the word or short phrase to learn
     * @param meaning what it means, in simple English
     * @param instead the learner's own words it could replace, or null
     */
    public record NewWord(String word, String meaning, String instead) {
    }

    public record FillerWord(String word, int count) {
    }

    /** @param score 0..100 from the pronunciation scorer */
    public record SoundSlip(String word, int score) {
    }

    public static TurnFeedback none() {
        return new TurnFeedback(List.of(), null);
    }

    /** The same notes plus what was measured from the learner's speech. */
    TurnFeedback withSpeech(List<FillerWord> fillers, List<SoundSlip> sounds) {
        return new TurnFeedback(corrections, tip, betterPhrasing, newWords, fillers, sounds);
    }

    /** Fills any missing native-language reasons from a plain template, so "Explain in my language" always works. */
    TurnFeedback withNativeFallback(ExplanationLanguage language) {
        if (language == null || language == ExplanationLanguage.ENGLISH) {
            return this;
        }
        List<Correction> filled = corrections.stream()
                .map(c -> c.whyNative() != null ? c
                        : new Correction(c.original(), c.better(), c.why(),
                                NativeNotes.sayInstead(language, c.original(), c.better())))
                .toList();
        return new TurnFeedback(filled, tip, betterPhrasing, newWords, fillerWords, mispronounced);
    }

    /** The shape we ask the model for; anything may be missing or wrong. */
    record Raw(List<RawCorrection> corrections, String tip, String betterPhrasing, List<RawNewWord> newWords) {

        Raw(List<RawCorrection> corrections, String tip) {
            this(corrections, tip, null, null);
        }
    }

    record RawCorrection(String original, String better, String why, String whyNative) {

        RawCorrection(String original, String better, String why) {
            this(original, better, why, null);
        }
    }

    record RawNewWord(String word, String meaning, String instead) {
    }

    static TurnFeedback fromRaw(Raw raw, String learnerText) {
        return fromRaw(raw, learnerText, ExplanationLanguage.ENGLISH);
    }

    /**
     * Keeps only corrections that quote what the learner actually said and actually change it, and native-language
     * reasons only when the learner asked for a language other than English. Returns null when the output can't be
     * used at all, so the caller falls back.
     */
    static TurnFeedback fromRaw(Raw raw, String learnerText, ExplanationLanguage language) {
        if (raw == null || raw.corrections() == null) {
            return null;
        }
        boolean nativeWanted = language != null && language != ExplanationLanguage.ENGLISH;
        String said = normalize(learnerText);
        List<Correction> kept = new ArrayList<>();
        for (RawCorrection candidate : raw.corrections()) {
            if (candidate == null || isBlank(candidate.original()) || isBlank(candidate.better())) {
                continue;
            }
            String original = candidate.original().strip();
            String better = candidate.better().strip();
            String normalizedOriginal = normalize(original);
            if (original.length() > MAX_ORIGINAL || better.length() > MAX_BETTER || normalizedOriginal.isEmpty()
                    || !said.contains(normalizedOriginal) || normalizedOriginal.equals(normalize(better))) {
                continue;
            }
            String why = isBlank(candidate.why()) ? null : truncate(candidate.why().strip(), MAX_WHY);
            String whyNative = !nativeWanted || isBlank(candidate.whyNative()) ? null
                    : truncate(candidate.whyNative().strip(), MAX_WHY_NATIVE);
            kept.add(new Correction(original, better, why, whyNative));
            if (kept.size() == MAX_CORRECTIONS) {
                break;
            }
        }
        String tip = isBlank(raw.tip()) ? null : truncate(raw.tip().strip(), MAX_TIP);
        return new TurnFeedback(kept, tip, betterPhrasing(raw.betterPhrasing(), said), newWords(raw.newWords(), said),
                List.of(), List.of());
    }

    /** Only a rewrite that changes something and is still about the length of a spoken answer. */
    private static String betterPhrasing(String candidate, String said) {
        if (isBlank(candidate)) {
            return null;
        }
        String phrasing = candidate.strip().replaceAll("^[\"“]|[\"”]$", "").strip();
        if (phrasing.isEmpty() || phrasing.length() > MAX_BETTER_PHRASING || normalize(phrasing).equals(said)) {
            return null;
        }
        return phrasing;
    }

    /** Words the learner didn't already use; "instead" must quote them, like a correction. */
    private static List<NewWord> newWords(List<RawNewWord> candidates, String said) {
        if (candidates == null) {
            return List.of();
        }
        String padded = " " + said + " ";
        List<NewWord> kept = new ArrayList<>();
        for (RawNewWord candidate : candidates) {
            if (candidate == null || isBlank(candidate.word()) || isBlank(candidate.meaning())) {
                continue;
            }
            String word = candidate.word().strip();
            String normalizedWord = normalize(word);
            if (word.length() > MAX_WORD || normalizedWord.isEmpty() || padded.contains(" " + normalizedWord + " ")) {
                continue;
            }
            String instead = isBlank(candidate.instead()) ? null : candidate.instead().strip();
            if (instead != null && (instead.length() > MAX_ORIGINAL || !said.contains(normalize(instead)))) {
                instead = null;
            }
            kept.add(new NewWord(word, truncate(candidate.meaning().strip(), MAX_MEANING), instead));
            if (kept.size() == MAX_NEW_WORDS) {
                break;
            }
        }
        return kept;
    }

    /** Lower case, plain apostrophes, letters and digits only: transcripts and model quotes differ in punctuation. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("[^\\p{L}\\p{N}' ]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1).strip() + "…";
    }
}
