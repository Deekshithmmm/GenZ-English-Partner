package com.genzenglish.partner.grammar;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.genzenglish.partner.grammar.GrammarCurriculum.ChartRow;
import com.genzenglish.partner.grammar.GrammarCurriculum.Example;
import com.genzenglish.partner.grammar.GrammarCurriculum.Kind;
import com.genzenglish.partner.practice.TurnFeedback;
import com.genzenglish.partner.profile.CefrLevel;

public final class GrammarDtos {

    private GrammarDtos() {
    }

    /**
     * DONE: finished at least once. CURRENT: the next lesson. OPEN: before the learner's starting point, there for
     * review. LOCKED: after the current lesson.
     */
    public enum NodeStatus {
        DONE,
        CURRENT,
        OPEN,
        LOCKED
    }

    /** @param currentNodeId the next lesson, or null once every lesson is done */
    public record PathResponse(List<Week> weeks, String currentNodeId, int done, int total) {
    }

    /** @param title the unit this week belongs to, e.g. "Telling stories" */
    public record Week(int number, String title, CefrLevel level, List<NodeItem> nodes) {
    }

    /**
     * @param number     position on the whole path, from 1
     * @param title      the lesson with its kind, e.g. "Learn: Introducing yourself"
     * @param topicTitle the topic alone, for places that show the kind separately
     * @param stars      best result so far, 0 to 3
     */
    public record NodeItem(String id, Kind kind, int number, String title, String topicTitle, String subtitle,
            String icon, NodeStatus status, int stars) {
    }

    /** A line from Luna, in English and (for learners who chose another language) in theirs. */
    public record TutorLine(String text, String nativeText) {
    }

    /** What Luna says during the speaking part and when it's over. */
    public record Phrases(TutorLine practice, TutorLine right, TutorLine almost, TutorLine done) {
    }

    public enum ExerciseType {
        CHOICE,
        BUILD
    }

    /**
     * CHOICE: pick one of {@code options}; {@code answer} is the right index. BUILD: tap {@code tiles} (the right words
     * plus a distractor or two, shuffled) into the order of {@code words}. Answers are included so the app can mark
     * each one straight away; the score is still worked out on the server.
     */
    public record Exercise(ExerciseType type, String instruction, String prompt, List<String> options, Integer answer,
            List<String> tiles, List<String> words, String explain) {
    }

    public enum SpeakType {
        /** Say a sentence with a slip the right way. */
        FIX,
        /** Answer a question out loud, using the topic. */
        ANSWER,
        /** A longer answer about yourself. */
        OPEN
    }

    /** @param sample a good answer (for FIX, the corrected sentence) */
    public record SpeakItem(SpeakType type, String instruction, String prompt, String sample, String tip) {
    }

    /**
     * One lesson on the path. A LEARN lesson is the intro, the cheat sheet and exercises, then a little speaking; a
     * fluency booster is mostly speaking.
     */
    public record LessonResponse(String id, Kind kind, int number, int week, String title, String topicTitle,
            String summary, String why, String icon, List<TutorLine> intro, List<ChartRow> chart, List<String> rules,
            List<Example> examples, List<Exercise> exercises, List<SpeakItem> speaking, Phrases phrases,
            NodeStatus status, int stars) {
    }

    public record SpeakTextRequest(
            @NotBlank(message = "Type your answer first.") @Size(max = 1500, message = "Keep it under 1,500 characters.") String text) {
    }

    /**
     * @param correct  the answer did what the item asked
     * @param expected a good way to say it
     * @param feedback Luna's notes on the answer, as in a chat
     */
    public record SpeakResult(String transcript, boolean correct, String expected, TurnFeedback feedback) {
    }

    /** @param choice for CHOICE; @param words the tiles in the order the learner put them, for BUILD */
    public record ExerciseAnswer(Integer choice, List<String> words) {
    }

    /**
     * @param answers   one per exercise, in order (null for one not answered)
     * @param spoken    what the learner said for each speaking item, in order (null for one skipped)
     * @param seconds   time spent in the lesson
     * @param attemptId the same for retries of one completion, so it's only counted once
     */
    public record CompleteRequest(@NotNull @Size(max = 30) List<ExerciseAnswer> answers,
            @NotNull @Size(max = 30) List<String> spoken, @Min(0) @Max(36_000) int seconds, UUID attemptId) {
    }

    /** One thing to do better: the rule, what the learner said, and the right way. */
    public record Improvement(String tip, String wrong, String right) {
    }

    /**
     * @param passed      at least half right, so the lesson counts as done
     * @param score       0 to 100
     * @param wordsUsed   English words the learner said or typed in the speaking part
     * @param note        Luna's note on the lesson
     * @param nextNodeId  the next lesson on the path, or null when the path is done
     */
    public record LessonReport(String nodeId, boolean passed, int score, int correct, int total, int stars, int xp,
            boolean firstTime, int wordsUsed, int seconds, String note, List<Improvement> improvements,
            String nextNodeId, Progress progress) {
    }

    public record Progress(int streak, int xpToday, int xpTotal) {
    }

    /** Stored as a finished lesson's summary, so a retried completion returns the same report. */
    record LessonResult(int score, int correct, int total, int stars, boolean firstTime, int wordsUsed, int seconds,
            String note, List<Improvement> improvements, UUID attemptId) {
    }
}
