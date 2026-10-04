package com.genzenglish.partner.practice;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.genzenglish.partner.practice.TurnFeedback.Correction;
import com.genzenglish.partner.speech.KaiLine;

public final class PracticeDtos {

    private PracticeDtos() {
    }

    /**
     * @param scenario the roleplay slug (e.g. {@code hr-interview}) or free-talk topic slug; ignored for a warm-up.
     *                 Free talk without a topic is an open chat.
     * @param context  for a mock interview: the learner's résumé, a job description, or both. Used to plan the
     *                 interview and not stored.
     */
    public record StartRequest(
            @NotNull(message = "Pick what to practise.") PracticeKind kind,
            @Size(max = 64) String scenario,
            @Size(max = InterviewPlanner.MAX_CONTEXT, message = "Keep it under 6,000 characters. The most recent "
                    + "parts of your résumé are enough.") String context) {

        public StartRequest(PracticeKind kind, String scenario) {
            this(kind, scenario, null);
        }
    }

    /**
     * @param brief       what the learner is doing: the warm-up topic, their goal in a roleplay, or the job in a mock
     *                    interview
     * @param targetTurns answers before Luna wraps up
     * @param criteria    what counts as success in a roleplay or interview; empty for other chats
     */
    public record StartResponse(
            UUID sessionId,
            PracticeKind kind,
            String scenario,
            String title,
            String brief,
            int turnNumber,
            int targetTurns,
            KaiLine kai,
            List<String> criteria) {
    }

    public record TextAnswerRequest(
            @NotNull @Min(1) @Max(MAX_TURNS) Integer turnNumber,
            @NotBlank(message = "Type your answer first.") @Size(max = 1500, message = "Keep your answer under 1,500 characters.") String text) {
    }

    /** No scene runs longer than this; it bounds turn numbers before the session is even loaded. */
    public static final int MAX_TURNS = 12;

    /**
     * @param feedback         corrections for this answer, shown under the learner's words
     * @param done             true once Luna has wrapped up; the app completes the session next
     * @param nextTurnNumber   null once done
     */
    public record AnswerResponse(
            int turnNumber,
            String transcript,
            Integer wordsPerMinute,
            int fillerCount,
            TurnFeedback feedback,
            boolean done,
            Integer nextTurnNumber,
            KaiLine kai) {
    }

    /** First event of a streamed answer: what the learner said, as heard. */
    public record TranscriptEvent(int turnNumber, String transcript, Integer wordsPerMinute, int fillerCount) {
    }

    /** Luna's reply so far, cleaned up for display. Each event replaces the last. */
    public record ReplyEvent(String text) {
    }

    /** Something went wrong after streaming started; {@code message} is shown to the learner as-is. */
    public record ProblemEvent(String code, String message) {
    }

    public record XpItem(String label, int xp) {
    }

    /**
     * @param averageWpm null when nothing was spoken out loud
     * @param topFiller  the filler word used most, or null
     */
    public record Stats(int answers, int voiceAnswers, int speakingSeconds, int wordsSpoken, Integer averageWpm,
            int fillerCount, String topFiller) {

        static Stats of(SessionStats s) {
            return new Stats(s.answers(), s.voiceAnswers(), s.speakingSeconds(), s.wordsSpoken(), s.averageWpm(),
                    s.fillerCount(), s.topFiller());
        }
    }

    /**
     * What a finished session earned. Stored as the session's summary, so completing again returns the same thing.
     * Sessions completed before scoring existed have no scores, focus or scorecard.
     *
     * @param note        Luna's two-sentence note: one thing that went well, one thing to try next time
     * @param corrections the corrections from the session worth another look
     * @param focus       the top things to work on next, most useful first
     * @param scorecard   how the learner did against a roleplay's or interview's success criteria; null otherwise
     */
    public record Summary(
            UUID sessionId,
            PracticeKind kind,
            String scenario,
            String title,
            Stats stats,
            int xp,
            List<XpItem> xpItems,
            String note,
            List<Correction> corrections,
            Scores scores,
            List<String> focus,
            Scorecard scorecard) {

        public Summary {
            focus = focus == null ? List.of() : List.copyOf(focus);
        }
    }

    /**
     * 0..100 for one session, against how a confident, fluent speaker sounds, so progress shows over time.
     *
     * @param pronunciation null when nothing was spoken out loud
     */
    public record Scores(int fluency, int grammar, Integer pronunciation, int vocabulary) {
    }

    public enum CriterionStatus {
        MET,
        PARTLY,
        MISSED
    }

    /** @param evidence the learner's own words that show it, or what was missing; may be null */
    public record CriterionResult(String criterion, CriterionStatus status, String evidence) {
    }

    public enum ScorecardVerdict {
        /** Every criterion met. */
        REACHED,
        /** At least half, counting "partly" as half. */
        ALMOST,
        NOT_YET
    }

    /** @param met criteria fully met, out of {@code total} */
    public record Scorecard(ScorecardVerdict verdict, int met, int total, List<CriterionResult> criteria) {

        public Scorecard {
            criteria = List.copyOf(criteria);
        }
    }

    /** The summary plus where the learner stands now, so the summary screen can celebrate a streak. */
    public record SummaryResponse(Summary summary, Progress progress) {
    }

    public record Progress(int streak, int xpToday, int xpTotal) {
    }

    public record CatalogResponse(List<TopicItem> topics, List<RoleplayItem> roleplays) {
    }

    public record TopicItem(String slug, String title) {
    }

    /**
     * @param goal what the learner has to get done in the scene
     */
    public record RoleplayItem(String slug, String title, String goal, RoleplayCatalog.Difficulty difficulty,
            int targetTurns) {
    }

    /**
     * @param ideas one or two things the learner could say next, as the learner
     */
    public record HintResponse(List<String> ideas) {
    }
}
