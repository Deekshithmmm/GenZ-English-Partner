package com.genzenglish.partner.practice;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.genzenglish.partner.ai.llm.LlmClient;
import com.genzenglish.partner.ai.llm.LlmMessage;
import com.genzenglish.partner.ai.llm.LlmRequest;
import com.genzenglish.partner.ai.llm.LlmTask;
import com.genzenglish.partner.common.Json;
import com.genzenglish.partner.practice.PracticeDtos.CriterionResult;
import com.genzenglish.partner.practice.PracticeDtos.CriterionStatus;
import com.genzenglish.partner.practice.PracticeDtos.Scores;
import com.genzenglish.partner.practice.SuccessCriteria.Criterion;
import com.genzenglish.partner.practice.TurnFeedback.Correction;

/**
 * Luna's review of a finished session: a two-sentence note (one thing that went well, one thing to try next time),
 * 0..100 scores, the top three things to work on, and for roleplays and interviews, how the learner did against the
 * scene's success criteria. One model call does all of it; each part that comes back unusable falls back to rules on
 * its own, so a good note is never thrown away because of a bad score.
 */
@Component
class SessionSummarizer {

    static final String SYSTEM_PROMPT = """
            You are Luna, an encouraging English speaking coach for Indian learners aged 18 to 26. You receive one \
            finished practice session: what kind it was, the learner's level, their answers (with pace in words per \
            minute and filler-word counts when they spoke), the corrections they were shown, words that didn't come \
            through clearly, and for roleplays and interviews, the scene's success criteria.
            Reply with only one JSON object, no other text:
            {"note":"...","scores":{"fluency":0,"grammar":0,"vocabulary":0},"focus":["...","...","..."],\
            "criteria":[{"status":"MET","evidence":"..."}]}
            - "note": a note to the learner, speaking to them as "you", in exactly two short sentences: first \
            something specific they did well (quote their words if it helps), then one concrete thing to try next \
            time. Plain, friendly English: no lists, no emojis, no levels or scores.
            - "scores": 0 to 100 for this session. Fluency: how long and connected the answers were, pace and \
            fillers. Grammar: how often and how seriously grammar slipped. Vocabulary: range and precision of words \
            for the situation. 90 and above sounds like a confident, fluent young professional; score against that, \
            not against their level, so they can see real progress over time. Very short answers can't score high \
            on fluency or vocabulary.
            - "focus": the three most useful things to work on next, most useful first, each a short instruction \
            under 12 words (for example: Use "for" with lengths of time).
            - "criteria": one entry per success criterion, in the same order, with "status" MET, PARTLY or MISSED \
            and "evidence" quoting the learner's words that show it, or saying in a few words what was missing. Use \
            an empty list when there are no criteria.
            """;
    static final int MAX_NOTE = 320;
    static final int MAX_FOCUS = 3;
    static final int MAX_FOCUS_CHARS = 120;
    private static final Logger log = LoggerFactory.getLogger(SessionSummarizer.class);

    private final LlmClient llm;
    private final Json json;

    SessionSummarizer(LlmClient llm, Json json) {
        this.llm = llm;
        this.json = json;
    }

    /**
     * The user message sent to the model; also parsed by the mock responder.
     *
     * @param goal            the warm-up topic, roleplay goal or interview role
     * @param correctionCount every correction shown in the session; {@code corrections} holds only the first few
     * @param unclearWords    words the pronunciation check flagged
     * @param criteria        the success criteria, empty for warm-ups and free talk
     */
    record Input(String kind, String title, String goal, String level, int speakingSeconds, Integer averageWpm,
            int fillerCount, String topFiller, List<Answer> answers, List<Correction> corrections, int correctionCount,
            List<String> unclearWords, List<String> criteria) {

        Input {
            answers = List.copyOf(answers);
            corrections = List.copyOf(corrections);
            unclearWords = unclearWords == null ? List.of() : List.copyOf(unclearWords);
            criteria = criteria == null ? List.of() : List.copyOf(criteria);
        }

        record Answer(String question, String answer, Integer wordsPerMinute, int fillerCount) {
        }

        List<String> answerTexts() {
            return answers.stream().map(Answer::answer).toList();
        }
    }

    record Raw(String note, RawScores scores, List<String> focus, List<RawCriterion> criteria) {
    }

    record RawScores(Integer fluency, Integer grammar, Integer vocabulary) {
    }

    record RawCriterion(String status, String evidence) {
    }

    /**
     * @param criteria one result per success criterion, in order; empty when there are none
     */
    record Review(String note, Scores scores, List<String> focus, List<CriterionResult> criteria) {
    }

    /**
     * @param criteria      the scene's success criteria, with the cues the fallback checks
     * @param pronunciation the session's measured pronunciation score, or null if nothing was spoken
     */
    Review review(Input input, List<Criterion> criteria, Integer pronunciation) {
        Raw raw = null;
        try {
            String text = llm.complete(LlmRequest.json(LlmTask.SESSION_SUMMARY, SYSTEM_PROMPT,
                    List.of(LlmMessage.user(json.write(input))), 700)).text();
            raw = json.readLenient(text, Raw.class);
            if (raw == null) {
                log.warn("Session summary JSON was unusable; using built-in rules");
            }
        } catch (RuntimeException e) {
            log.warn("Session summary failed ({}); using built-in rules", llm.providerName(), e);
        }
        return fromRaw(raw, input, criteria, pronunciation);
    }

    /** Takes each usable part of the model's review and fills the rest from rules. */
    static Review fromRaw(Raw raw, Input input, List<Criterion> criteria, Integer pronunciation) {
        Raw review = raw == null ? new Raw(null, null, null, null) : raw;
        String note = review.note() != null && !review.note().isBlank() && review.note().strip().length() <= MAX_NOTE
                ? review.note().strip()
                : fallbackNote(input);
        RawScores scores = review.scores() == null ? new RawScores(null, null, null) : review.scores();
        List<String> answers = input.answerTexts();
        Scores merged = new Scores(
                valid(scores.fluency()) ? scores.fluency()
                        : SessionScores.fluency(answers, input.averageWpm(), input.fillerCount()),
                valid(scores.grammar()) ? scores.grammar() : SessionScores.grammar(answers, input.correctionCount()),
                pronunciation,
                valid(scores.vocabulary()) ? scores.vocabulary() : SessionScores.vocabulary(answers));
        List<CriterionResult> results = criteria(review.criteria(), criteria, answers);
        List<String> focus = focus(review.focus());
        if (focus.isEmpty()) {
            focus = fallbackFocus(input, results);
        }
        return new Review(note, merged, focus, results);
    }

    private static boolean valid(Integer score) {
        return score != null && score >= 0 && score <= 100;
    }

    private static List<String> focus(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.strip().replaceAll("^[-•*\\d.)\\s]+", ""))
                .filter(item -> !item.isEmpty() && item.length() <= MAX_FOCUS_CHARS)
                .distinct()
                .limit(MAX_FOCUS)
                .toList();
    }

    /** The model's verdicts when there's exactly one per criterion; otherwise the cue-based check. */
    private static List<CriterionResult> criteria(List<RawCriterion> raw, List<Criterion> criteria,
            List<String> answers) {
        if (criteria.isEmpty()) {
            return List.of();
        }
        if (raw == null || raw.size() != criteria.size()) {
            return SuccessCriteria.evaluate(criteria, answers);
        }
        List<CriterionResult> results = new ArrayList<>(criteria.size());
        for (int i = 0; i < criteria.size(); i++) {
            RawCriterion verdict = raw.get(i);
            CriterionStatus status = verdict == null ? null : status(verdict.status());
            if (status == null) {
                return SuccessCriteria.evaluate(criteria, answers);
            }
            String evidence = verdict.evidence() == null || verdict.evidence().isBlank() ? null
                    : SuccessCriteria.quote(verdict.evidence());
            results.add(new CriterionResult(criteria.get(i).text(), status, evidence));
        }
        return List.copyOf(results);
    }

    private static CriterionStatus status(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "MET", "YES" -> CriterionStatus.MET;
            case "PARTLY", "PARTIAL", "PARTIALLY" -> CriterionStatus.PARTLY;
            case "MISSED", "NO", "NOT_MET" -> CriterionStatus.MISSED;
            default -> null;
        };
    }

    /** Specific even without a model: it uses the learner's own numbers, corrections and fillers. */
    static String fallbackNote(Input input) {
        int answers = input.answers().size();
        int fillers = input.answers().stream().mapToInt(Input.Answer::fillerCount).sum();
        double averageWords = averageWords(input);
        String praise = input.speakingSeconds() >= 30
                ? "You spoke for " + duration(input.speakingSeconds()) + " across " + count(answers, "answer")
                        + ", and that's real practice."
                : "You answered " + count(answers, "time") + " and kept the conversation moving.";
        String next;
        if (!input.corrections().isEmpty()) {
            Correction first = input.corrections().getFirst();
            next = "Next time, try \"" + first.better() + "\" instead of \"" + first.original() + "\".";
        } else if (input.topFiller() != null && fillers >= 2) {
            next = "Next time, swap \"" + input.topFiller() + "\" for a short, calm pause.";
        } else if (averageWords < 12) {
            next = "Next time, stretch each answer to two or three sentences.";
        } else {
            next = "Next time, try a longer story with a clear beginning, middle and end.";
        }
        return praise + " " + next;
    }

    /**
     * The three most useful next steps, in order: the scene's goal first (a missed criterion), then the learner's
     * own slips, sounds, fillers, pace and answer length, then general habits.
     */
    static List<String> fallbackFocus(Input input, List<CriterionResult> criteria) {
        Set<String> focus = new LinkedHashSet<>();
        criteria.stream()
                .filter(c -> c.status() != CriterionStatus.MET)
                .findFirst()
                .ifPresent(c -> focus.add(c.criterion()));
        input.corrections().stream()
                .limit(2)
                .forEach(c -> focus.add("Say \"" + c.better() + "\", not \"" + c.original() + "\""));
        input.unclearWords().stream().findFirst().ifPresent(w -> focus.add("Practise saying \"" + w + "\" clearly"));
        if (input.topFiller() != null && input.fillerCount() >= 2) {
            focus.add("Swap \"" + input.topFiller() + "\" for a short, calm pause");
        }
        Integer wpm = input.averageWpm();
        if (wpm != null && wpm > 170) {
            focus.add("Slow down a little: aim for 120 to 150 words a minute");
        } else if (wpm != null && wpm < 90) {
            focus.add("Speak in longer runs, without stopping between words");
        }
        if (averageWords(input) < 12) {
            focus.add("Stretch each answer to two or three sentences");
        }
        // Habits worth building in any answer, so there are always three things to try.
        focus.add("Add one specific example to each answer");
        focus.add("Tell a longer story with a clear beginning, middle and end");
        focus.add("Link your ideas with \"because\", \"so\" and \"after that\"");
        return focus.stream().filter(f -> f.length() <= MAX_FOCUS_CHARS).limit(MAX_FOCUS).toList();
    }

    private static double averageWords(Input input) {
        return input.answers().stream()
                .mapToInt(a -> a.answer() == null || a.answer().isBlank() ? 0 : a.answer().strip().split("\\s+").length)
                .average()
                .orElse(0);
    }

    static String duration(int seconds) {
        int minutes = seconds / 60;
        int rest = seconds % 60;
        if (minutes == 0) {
            return count(rest, "second");
        }
        return rest == 0 ? count(minutes, "minute") : count(minutes, "minute") + " " + count(rest, "second");
    }

    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }
}
