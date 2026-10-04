package com.genzenglish.partner.practice;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rule-based 0..100 scores for a session, used when the model's scores are missing or unusable. They're deliberately
 * simple and stable, so a learner who speaks longer, smoother and more accurately sees the numbers move:
 * <ul>
 * <li>fluency: answer length, pace and filler words</li>
 * <li>grammar: corrections per hundred words</li>
 * <li>vocabulary: how many different words the learner used for the amount they said</li>
 * </ul>
 * Short sessions are pulled towards the middle, because a few words aren't enough evidence for a high or low score.
 */
final class SessionScores {

    /** A comfortable answer when speaking: two or three full sentences. */
    static final double FULL_ANSWER_WORDS = 25;
    static final int EASY_PACE_MIN = 110;
    static final int EASY_PACE_MAX = 160;

    private SessionScores() {
    }

    static int fluency(List<String> answers, Integer averageWpm, int fillerCount) {
        int words = wordCount(answers);
        double perAnswer = answers.isEmpty() ? 0 : (double) words / answers.size();
        double length = Math.min(100, perAnswer / FULL_ANSWER_WORDS * 100);
        double fillersPerHundred = words == 0 ? 0 : fillerCount * 100.0 / words;
        double fillers = clamp(100 - fillersPerHundred * 8, 0, 100);
        double score;
        if (averageWpm == null) {
            score = 0.6 * length + 0.4 * fillers;
        } else {
            double distance = averageWpm < EASY_PACE_MIN ? EASY_PACE_MIN - averageWpm
                    : Math.max(0, averageWpm - EASY_PACE_MAX);
            double pace = clamp(100 - distance * 1.2, 30, 100);
            score = 0.45 * length + 0.25 * pace + 0.3 * fillers;
        }
        return (int) Math.round(clamp(score, 10, 98));
    }

    static int grammar(List<String> answers, int correctionCount) {
        int words = wordCount(answers);
        double perHundred = correctionCount * 100.0 / Math.max(words, 1);
        double raw = clamp(96 - perHundred * 9, 25, 98);
        return (int) Math.round(towardsMiddle(raw, 70, words, 40));
    }

    /** Guiraud's index (different words / √words), which unlike a plain ratio doesn't punish longer answers. */
    static int vocabulary(List<String> answers) {
        List<String> words = words(answers);
        if (words.isEmpty()) {
            return 20;
        }
        Set<String> distinct = new HashSet<>(words);
        double guiraud = distinct.size() / Math.sqrt(words.size());
        double longWords = distinct.stream().filter(w -> w.length() >= 7).count() / (double) distinct.size();
        double raw = clamp(30 + (guiraud - 3) * 15 + Math.min(10, longWords * 50), 20, 95);
        return (int) Math.round(towardsMiddle(raw, 50, words.size(), 30));
    }

    /** Blends {@code raw} towards {@code middle} until there are {@code enough} words to trust it. */
    private static double towardsMiddle(double raw, double middle, int words, int enough) {
        double weight = Math.min(1, (double) words / enough);
        return middle + (raw - middle) * weight;
    }

    private static int wordCount(List<String> answers) {
        return words(answers).size();
    }

    private static List<String> words(List<String> answers) {
        return answers.stream()
                .flatMap(answer -> SpeechMetrics.tokenize(answer).stream())
                .filter(token -> !token.equals(",") && token.chars().anyMatch(Character::isLetter))
                .toList();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
