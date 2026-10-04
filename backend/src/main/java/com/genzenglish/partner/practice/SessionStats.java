package com.genzenglish.partner.practice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the learner did in one session, from its stored turns.
 *
 * @param averageWpm null when no answer was spoken (or all were too short to time)
 * @param topFiller  the filler word used most, or null if none
 */
record SessionStats(int answers, int voiceAnswers, int speakingSeconds, int wordsSpoken, Integer averageWpm,
        int fillerCount, String topFiller) {

    static SessionStats of(List<Turn> turns, int speakingMs) {
        int voice = 0;
        int words = 0;
        int wpmSum = 0;
        int wpmCount = 0;
        Map<String, Integer> fillers = new LinkedHashMap<>();
        for (Turn turn : turns) {
            Integer durationMs = turn.getAudioDurationMs();
            SpeechMetrics.Stats stats = SpeechMetrics.analyze(turn.getLearnerText(),
                    durationMs == null ? null : durationMs.longValue());
            if (turn.getInputMode() == InputMode.VOICE) {
                voice++;
            }
            words += stats.wordCount();
            if (stats.wordsPerMinute() != null) {
                wpmSum += stats.wordsPerMinute();
                wpmCount++;
            }
            stats.fillers().forEach((filler, count) -> fillers.merge(filler, count, Integer::sum));
        }
        String topFiller = fillers.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        int fillerCount = fillers.values().stream().mapToInt(Integer::intValue).sum();
        return new SessionStats(turns.size(), voice, Math.round(speakingMs / 1000f), words,
                wpmCount == 0 ? null : Math.round((float) wpmSum / wpmCount), fillerCount, topFiller);
    }
}
