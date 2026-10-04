package com.genzenglish.partner.practice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pace and filler-word counts from a transcript. "Like" only counts as a filler when it isn't doing grammatical
 * work ("I like coding", "looks like rain", "I'd like to" are fine).
 */
public final class SpeechMetrics {

    private static final String COMMA = ",";
    private static final Map<String, String> HESITATIONS = Map.ofEntries(
            Map.entry("um", "um"), Map.entry("umm", "um"), Map.entry("ummm", "um"), Map.entry("uhm", "um"),
            Map.entry("uh", "uh"), Map.entry("uhh", "uh"), Map.entry("er", "uh"), Map.entry("erm", "uh"),
            Map.entry("hmm", "hmm"), Map.entry("hm", "hmm"));
    private static final Set<String> FILLER_WORDS = Set.of("basically", "actually", "literally");
    private static final Set<String> LIKE_AS_VERB_OR_PREPOSITION_BEFORE = Set.of(
            "i", "you", "we", "they", "he", "she", "i'd", "you'd", "we'd", "they'd", "would", "should", "could",
            "do", "don't", "does", "doesn't", "did", "didn't", "not", "really", "also", "to", "just", "look", "looks",
            "looked", "looking", "feel", "feels", "felt", "sound", "sounds", "sounded", "seem", "seems", "something",
            "anything", "nothing", "more", "much", "people", "who", "is", "was", "be");
    private static final Set<String> YOU_KNOW_OBJECTS = Set.of(
            "what", "how", "that", "why", "where", "who", "the", "a", "an", "him", "her", "them", "it", "about", "if");

    private SpeechMetrics() {
    }

    /**
     * @param wordsPerMinute null when the duration is unknown (typed input) or too short to be meaningful
     */
    public record Stats(int wordCount, Integer wordsPerMinute, int fillerCount, Map<String, Integer> fillers) {
    }

    public static Stats analyze(String transcript, Long durationMs) {
        List<String> tokens = tokenize(transcript);
        List<String> words = tokens.stream().filter(t -> !t.equals(COMMA)).toList();
        Map<String, Integer> fillers = new LinkedHashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            String word = tokens.get(i);
            if (word.equals(COMMA)) {
                continue;
            }
            String before = i > 0 ? tokens.get(i - 1) : null;
            String after = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            String previous = previousWord(tokens, i);
            String next = nextWord(tokens, i);
            String filler = null;
            if (HESITATIONS.containsKey(word)) {
                filler = HESITATIONS.get(word);
            } else if (FILLER_WORDS.contains(word)) {
                filler = word;
            } else if (word.equals("like") && (COMMA.equals(before) || COMMA.equals(after)
                    || isFillerLike(previous, next))) {
                // ", like," set off by commas is almost always a filler, whatever comes before it.
                filler = "like";
            } else if (word.equals("know") && "you".equals(previous) && (next == null || !YOU_KNOW_OBJECTS.contains(next))) {
                filler = "you know";
            } else if (word.equals("mean") && "i".equals(previous) && (next == null || !Set.of("it", "that", "to").contains(next))) {
                filler = "i mean";
            }
            if (filler != null) {
                fillers.merge(filler, 1, Integer::sum);
            }
        }
        int fillerCount = fillers.values().stream().mapToInt(Integer::intValue).sum();
        Integer wpm = null;
        if (durationMs != null && durationMs >= 1500 && !words.isEmpty()) {
            wpm = (int) Math.round(words.size() * 60_000.0 / durationMs);
        }
        return new Stats(words.size(), wpm, fillerCount, Collections.unmodifiableMap(fillers));
    }

    /** Lower-cased words, with commas kept as their own tokens because they signal discourse fillers. */
    static List<String> tokenize(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return List.of();
        }
        String cleaned = transcript.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replace(",", " " + COMMA + " ")
                .replaceAll("[^a-z0-9', ]", " ");
        List<String> tokens = new ArrayList<>();
        for (String token : cleaned.split("\\s+")) {
            String word = token.equals(COMMA) ? token : token.replaceAll("^'+|'+$", "");
            if (!word.isEmpty()) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private static String previousWord(List<String> tokens, int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (!tokens.get(i).equals(COMMA)) {
                return tokens.get(i);
            }
        }
        return null;
    }

    private static String nextWord(List<String> tokens, int index) {
        for (int i = index + 1; i < tokens.size(); i++) {
            if (!tokens.get(i).equals(COMMA)) {
                return tokens.get(i);
            }
        }
        return null;
    }

    private static boolean isFillerLike(String previous, String next) {
        if (previous != null && LIKE_AS_VERB_OR_PREPOSITION_BEFORE.contains(previous)) {
            return false;
        }
        return next == null || !next.equals("to");
    }
}
