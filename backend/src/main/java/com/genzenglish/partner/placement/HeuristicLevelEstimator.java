package com.genzenglish.partner.placement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.genzenglish.partner.practice.SpeechMetrics;
import com.genzenglish.partner.profile.CefrLevel;

/**
 * A rough CEFR estimate from transcripts alone: pace, fillers, answer length, word variety and how ideas are linked.
 * Used when the LLM is unavailable or returns something unusable, and by the mock LLM. It can't judge grammar
 * properly, so it errs towards the middle of the scale.
 */
public final class HeuristicLevelEstimator {

    private static final Set<String> BASIC_LINKERS = Set.of("because", "but", "so", "if", "when", "then", "after",
            "before", "also");
    private static final Set<String> ADVANCED_LINKERS = Set.of("although", "however", "whereas", "unless",
            "therefore", "moreover", "despite", "instead", "whether", "meanwhile", "otherwise", "which", "whose",
            "though", "while", "since", "until", "overall");
    private static final Set<String> PAST_FORMS = Set.of("was", "were", "had", "went", "took", "got", "came", "saw",
            "did", "made", "said", "told", "thought", "felt", "left", "found", "missed", "reached", "decided",
            "tried", "realised", "realized", "happened", "started", "ended", "forgot", "lost", "wanted");
    private static final Set<String> MODALS = Set.of("would", "could", "might", "should", "may", "probably",
            "perhaps", "maybe", "depends");

    private HeuristicLevelEstimator() {
    }

    public record Sample(String question, String transcript, Long durationMs) {
    }

    public static PlacementAssessment estimate(List<Sample> samples) {
        int answers = Math.max(1, samples.size());
        int totalWords = 0;
        int fillers = 0;
        int wpmSum = 0;
        int wpmCount = 0;
        int longWords = 0;
        int basicLinkers = 0;
        int advancedLinkers = 0;
        int pastForms = 0;
        int modals = 0;
        double varietySum = 0;

        for (Sample sample : samples) {
            List<String> words = tokenize(sample.transcript());
            SpeechMetrics.Stats stats = SpeechMetrics.analyze(sample.transcript(), sample.durationMs());
            totalWords += words.size();
            fillers += stats.fillerCount();
            if (stats.wordsPerMinute() != null) {
                wpmSum += stats.wordsPerMinute();
                wpmCount++;
            }
            varietySum += words.isEmpty() ? 0 : (double) new HashSet<>(words).size() / words.size();
            for (String word : words) {
                if (word.length() >= 7) {
                    longWords++;
                }
                if (BASIC_LINKERS.contains(word)) {
                    basicLinkers++;
                }
                if (ADVANCED_LINKERS.contains(word)) {
                    advancedLinkers++;
                }
                if (PAST_FORMS.contains(word) || (word.length() > 4 && word.endsWith("ed"))) {
                    pastForms++;
                }
                if (MODALS.contains(word)) {
                    modals++;
                }
            }
        }

        double avgWords = (double) totalWords / answers;
        double fillerRate = totalWords == 0 ? 0 : (double) fillers / totalWords;
        Integer wpm = wpmCount == 0 ? null : wpmSum / wpmCount;
        double longWordRate = totalWords == 0 ? 0 : (double) longWords / totalWords;
        double variety = varietySum / answers;

        int fluency = PlacementAssessment.clamp((int) Math.round(
                paceScore(wpm) - fillerRate * 150 - lengthPenalty(avgWords)));
        int grammar = PlacementAssessment.clamp((int) Math.round(
                35 + Math.min(20, basicLinkers * 3.0) + Math.min(24, advancedLinkers * 6.0)
                        + Math.min(10, pastForms * 2.0) + Math.min(8, modals * 2.0) - lengthPenalty(avgWords)));
        int vocabulary = PlacementAssessment.clamp((int) Math.round(
                20 + variety * 35 + longWordRate * 120 + Math.min(10, advancedLinkers * 2.5)
                        - lengthPenalty(avgWords)));

        double overall = fluency * 0.4 + grammar * 0.3 + vocabulary * 0.3;
        CefrLevel level = overall < 35 ? CefrLevel.A1
                : overall < 50 ? CefrLevel.A2
                : overall < 65 ? CefrLevel.B1
                : overall < 80 ? CefrLevel.B2
                : CefrLevel.C1;

        List<String> strengths = new ArrayList<>();
        if (wpm != null && wpm >= 100) {
            strengths.add("You keep talking without long pauses.");
        }
        if (totalWords > 0 && fillerRate < 0.03) {
            strengths.add("You rarely lean on filler words like \"um\" or \"basically\".");
        }
        if (advancedLinkers >= 2) {
            strengths.add("You link ideas with words like \"although\" and \"which\".");
        }
        if (avgWords >= 20) {
            strengths.add("You give full answers, not one-liners.");
        }
        if (strengths.isEmpty()) {
            strengths.add("You spoke up and answered every question you could. That's the hardest part.");
        }

        List<String> focus = new ArrayList<>();
        if (fillerRate >= 0.03) {
            focus.add("Swap fillers like \"basically\" and \"um\" for a short, calm pause.");
        }
        if (avgWords < 12) {
            focus.add("Stretch your answers to two or three sentences.");
        }
        if (wpm != null && wpm < 80) {
            focus.add("Build speed with short daily warm-ups so words come quicker.");
        }
        if (advancedLinkers < 1) {
            focus.add("Link ideas with \"because\", \"although\" and \"so\" to sound more natural.");
        }
        if (pastForms < 2) {
            focus.add("Use past tense for stories: \"I missed the train\", not \"I am missing\".");
        }
        for (String fallback : GENERAL_FOCUS) {
            if (focus.size() >= 3) {
                break;
            }
            focus.add(fallback);
        }

        return new PlacementAssessment(level, fluency, grammar, vocabulary,
                List.copyOf(strengths.subList(0, Math.min(3, strengths.size()))), List.copyOf(focus.subList(0, 3)),
                summary(fluency, grammar, vocabulary));
    }

    /**
     * Names the learner's strongest and weakest areas. The app already shows the level's description, so the summary
     * shouldn't repeat it.
     */
    static String summary(int fluency, int grammar, int vocabulary) {
        Map<String, Integer> areas = new LinkedHashMap<>();
        areas.put("fluency", fluency);
        areas.put("grammar", grammar);
        areas.put("vocabulary", vocabulary);
        String strongest = Collections.max(areas.entrySet(), Map.Entry.comparingByValue()).getKey();
        String weakest = Collections.min(areas.entrySet(), Map.Entry.comparingByValue()).getKey();
        if (areas.get(strongest) - areas.get(weakest) < 8) {
            return "Your fluency, grammar and vocabulary are evenly matched, so daily practice will lift all three "
                    + "together.";
        }
        return capitalize(strongest) + " is your strongest area. A little practice on " + weakest
                + " will make the biggest difference.";
    }

    private static String capitalize(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    private static final List<String> GENERAL_FOCUS = List.of(
            "Pick precise words instead of \"very\" and \"good\".",
            "Practise answering follow-up questions without preparing first.",
            "Try new phrases from each chat in your next one.");

    private static double paceScore(Integer wpm) {
        if (wpm == null) {
            return 55;
        }
        if (wpm < 40) {
            return 25;
        }
        if (wpm < 70) {
            return 25 + (wpm - 40);
        }
        if (wpm < 110) {
            return 55 + (wpm - 70) * 0.75;
        }
        if (wpm <= 160) {
            return 85 + (wpm - 110) * 0.2;
        }
        return 90;
    }

    private static double lengthPenalty(double avgWords) {
        if (avgWords < 6) {
            return 25;
        }
        if (avgWords < 12) {
            return 12;
        }
        return 0;
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> words = new ArrayList<>();
        for (String token : text.toLowerCase().replaceAll("[^a-z' ]", " ").split("\\s+")) {
            if (!token.isBlank()) {
                words.add(token);
            }
        }
        return words;
    }
}
