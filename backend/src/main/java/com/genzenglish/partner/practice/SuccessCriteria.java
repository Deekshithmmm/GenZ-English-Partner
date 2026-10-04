package com.genzenglish.partner.practice;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.genzenglish.partner.practice.PracticeDtos.CriterionResult;
import com.genzenglish.partner.practice.PracticeDtos.CriterionStatus;
import com.genzenglish.partner.practice.PracticeDtos.Scorecard;
import com.genzenglish.partner.practice.PracticeDtos.ScorecardVerdict;

/**
 * What a learner has to do for a roleplay or interview to count as a success, and the scorecard at the end. The model
 * judges the criteria when it can; otherwise each criterion is checked against cue phrases in what the learner said,
 * which can only tell "met" from "missed" but always quotes the learner's own words as evidence.
 */
public final class SuccessCriteria {

    static final int MAX_EVIDENCE = 160;

    private SuccessCriteria() {
    }

    /**
     * @param text what the learner should do, written as an instruction ("Ask a question about the menu")
     * @param cues matched against the learner's words, lower case with punctuation removed
     */
    public record Criterion(String text, Pattern cues) {

        /** @param cues regex fragments: plain words and phrases, or patterns like {@code \\d{3,}} */
        public static Criterion of(String text, String... cues) {
            return new Criterion(text, Pattern.compile("(?<![\\p{L}\\p{N}'])(?:" + String.join("|", cues)
                    + ")(?![\\p{L}\\p{N}'])"));
        }
    }

    /** Cue-based check: a criterion is met when any sentence the learner said contains one of its cues. */
    static List<CriterionResult> evaluate(List<Criterion> criteria, List<String> answers) {
        List<CriterionResult> results = new ArrayList<>(criteria.size());
        for (Criterion criterion : criteria) {
            String evidence = firstSentenceMatching(criterion.cues(), answers);
            results.add(evidence == null ? new CriterionResult(criterion.text(), CriterionStatus.MISSED, null)
                    : new CriterionResult(criterion.text(), CriterionStatus.MET, evidence));
        }
        return List.copyOf(results);
    }

    private static String firstSentenceMatching(Pattern cues, List<String> answers) {
        for (String answer : answers) {
            if (answer == null) {
                continue;
            }
            for (String sentence : answer.strip().split("(?<=[.!?])\\s+")) {
                if (cues.matcher(TurnFeedback.normalize(sentence)).find()) {
                    return quote(sentence);
                }
            }
        }
        return null;
    }

    static String quote(String sentence) {
        String text = sentence.strip();
        return text.length() <= MAX_EVIDENCE ? text : text.substring(0, MAX_EVIDENCE - 1).strip() + "…";
    }

    /** Partly-met criteria count as half, so "almost there" means at least half of the scene got done. */
    static Scorecard scorecard(List<CriterionResult> results) {
        int met = (int) results.stream().filter(r -> r.status() == CriterionStatus.MET).count();
        int partly = (int) results.stream().filter(r -> r.status() == CriterionStatus.PARTLY).count();
        ScorecardVerdict verdict;
        if (met == results.size()) {
            verdict = ScorecardVerdict.REACHED;
        } else if (met * 2 + partly >= results.size()) {
            verdict = ScorecardVerdict.ALMOST;
        } else {
            verdict = ScorecardVerdict.NOT_YET;
        }
        return new Scorecard(verdict, met, results.size(), results);
    }
}
