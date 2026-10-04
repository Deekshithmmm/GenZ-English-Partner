package com.genzenglish.partner.placement;

import java.util.List;
import java.util.Objects;

import com.genzenglish.partner.profile.CefrLevel;

/**
 * The placement verdict. Stored as the session summary and returned to the app.
 */
public record PlacementAssessment(
        CefrLevel level,
        int fluency,
        int grammar,
        int vocabulary,
        List<String> strengths,
        List<String> focusAreas,
        String summary) {

    /** The shape we ask the model to return; every field may be missing or malformed. */
    record Raw(String level, Integer fluency, Integer grammar, Integer vocabulary, List<String> strengths,
            List<String> focusAreas, String summary) {
    }

    /** Validates model output; returns null when it can't be trusted so the caller falls back. */
    static PlacementAssessment fromRaw(Raw raw) {
        if (raw == null || raw.level() == null || raw.fluency() == null || raw.grammar() == null
                || raw.vocabulary() == null) {
            return null;
        }
        CefrLevel level;
        try {
            level = CefrLevel.valueOf(raw.level().strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
        List<String> focus = clean(raw.focusAreas());
        if (focus.isEmpty()) {
            return null;
        }
        int fluency = clamp(raw.fluency());
        int grammar = clamp(raw.grammar());
        int vocabulary = clamp(raw.vocabulary());
        String summary = raw.summary() == null || raw.summary().isBlank()
                ? HeuristicLevelEstimator.summary(fluency, grammar, vocabulary)
                : raw.summary().strip();
        return new PlacementAssessment(level, fluency, grammar, vocabulary, clean(raw.strengths()), focus, summary);
    }

    private static List<String> clean(List<String> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream().filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).limit(3)
                .toList();
    }

    static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
