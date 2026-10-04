package com.genzenglish.partner.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.genzenglish.partner.practice.PracticeDtos.CriterionResult;
import com.genzenglish.partner.practice.PracticeDtos.CriterionStatus;
import com.genzenglish.partner.practice.PracticeDtos.Scores;
import com.genzenglish.partner.practice.SessionSummarizer.Input;
import com.genzenglish.partner.practice.SessionSummarizer.Raw;
import com.genzenglish.partner.practice.SessionSummarizer.RawCriterion;
import com.genzenglish.partner.practice.SessionSummarizer.RawScores;
import com.genzenglish.partner.practice.SessionSummarizer.Review;
import com.genzenglish.partner.practice.SuccessCriteria.Criterion;
import com.genzenglish.partner.practice.TurnFeedback.Correction;

/** The end-of-session review keeps each usable part of the model's answer and fills the rest from rules. */
class SessionReviewTest {

    private static final List<Criterion> CAFE = RoleplayCatalog.find("ordering-at-cafe").orElseThrow().criteria();
    private static final String ORDER = "Can I get one cold coffee and a veg sandwich? Do you have oat milk? "
            + "I will pay by UPI.";

    @Test
    void usableModelPartsAreKeptAndPronunciationIsAlwaysMeasured() {
        Raw raw = new Raw("  You ordered clearly. Next time, ask one more question.  ", new RawScores(81, 140, 66),
                List.of("1. Ask about prices", "- Say \"for\" with lengths of time", "", "Keep it up"),
                List.of(new RawCriterion("met", "Can I get one cold coffee"), new RawCriterion("PARTIAL", null),
                        new RawCriterion("no", "Didn't pay")));

        Review review = SessionSummarizer.fromRaw(raw, input(List.of(ORDER), List.of(), 0), CAFE, 72);

        assertThat(review.note()).isEqualTo("You ordered clearly. Next time, ask one more question.");
        assertThat(review.scores().fluency()).isEqualTo(81);
        assertThat(review.scores().grammar()).as("out of range, so the rule-based score is used")
                .isEqualTo(SessionScores.grammar(List.of(ORDER), 0));
        assertThat(review.scores().pronunciation()).isEqualTo(72);
        assertThat(review.scores().vocabulary()).isEqualTo(66);
        assertThat(review.focus()).containsExactly("Ask about prices", "Say \"for\" with lengths of time",
                "Keep it up");
        assertThat(review.criteria()).extracting(CriterionResult::status)
                .containsExactly(CriterionStatus.MET, CriterionStatus.PARTLY, CriterionStatus.MISSED);
        assertThat(review.criteria().getFirst().criterion()).isEqualTo(CAFE.getFirst().text());
    }

    @Test
    void criteriaFallBackToCuesWhenTheModelMiscounts() {
        Raw raw = new Raw(null, null, null, List.of(new RawCriterion("MET", "Everything")));

        Review review = SessionSummarizer.fromRaw(raw, input(List.of(ORDER), List.of(), 0), CAFE, null);

        assertThat(review.criteria()).extracting(CriterionResult::status)
                .containsExactly(CriterionStatus.MET, CriterionStatus.MET, CriterionStatus.MET);
        assertThat(review.criteria().getLast().evidence()).isEqualTo("I will pay by UPI.");
        assertThat(review.scores().pronunciation()).isNull();
    }

    @Test
    void withoutTheModelEverythingComesFromRules() {
        Input input = input(List.of("Yes.", "I am having two years experience."),
                List.of(new Correction("I am having", "I have", null)), 2);

        Review review = SessionSummarizer.fromRaw(null, input, CAFE, null);

        assertThat(review.note()).isEqualTo(SessionSummarizer.fallbackNote(input));
        assertThat(review.scores()).isEqualTo(new Scores(
                SessionScores.fluency(input.answerTexts(), null, 2),
                SessionScores.grammar(input.answerTexts(), 1), null, SessionScores.vocabulary(input.answerTexts())));
        // The scene's goal comes first, then the learner's own slip.
        assertThat(review.focus()).hasSize(3).startsWith("Order your drinks and something to eat",
                "Say \"I have\", not \"I am having\"");
    }

    @Test
    void fallbackFocusUsesTheLearnersOwnSlipsSoundsAndFillers() {
        Input input = new Input("FREE_TALK", "Talk with Luna", "Music", "B1", 90, 180, 4, "like",
                List.of(answer("I like, like, listening to music from, like, the nineties and some indie bands too.", 4)),
                List.of(), 0, List.of("thirty"), List.of());

        assertThat(SessionSummarizer.fallbackFocus(input, List.of())).containsExactly(
                "Practise saying \"thirty\" clearly",
                "Swap \"like\" for a short, calm pause",
                "Slow down a little: aim for 120 to 150 words a minute");
    }

    @Test
    void scoresRewardLongerCleanerAnswers() {
        List<String> short1 = List.of("Yes.", "Fine.", "Okay.");
        List<String> full = List.of(
                "Last weekend I went trekking near Coorg with three college friends, and the view from the top "
                        + "was absolutely worth the climb.",
                "We started before sunrise, carried our own food, and reached the summit just as the clouds "
                        + "were clearing away.",
                "Next time I'd like to try a longer trail, maybe something in the Himalayas during the summer "
                        + "holidays.");

        assertThat(SessionScores.fluency(full, 135, 0)).isGreaterThan(SessionScores.fluency(short1, 135, 0));
        assertThat(SessionScores.fluency(full, 135, 0)).isGreaterThan(SessionScores.fluency(full, 135, 12));
        assertThat(SessionScores.fluency(full, 135, 0)).isGreaterThan(SessionScores.fluency(full, 60, 0));
        assertThat(SessionScores.grammar(full, 0)).isGreaterThan(SessionScores.grammar(full, 3));
        assertThat(SessionScores.vocabulary(full)).isGreaterThan(SessionScores.vocabulary(short1));
        for (int score : List.of(SessionScores.fluency(short1, null, 0), SessionScores.grammar(short1, 5),
                SessionScores.vocabulary(short1), SessionScores.vocabulary(List.of()))) {
            assertThat(score).isBetween(0, 100);
        }
    }

    @Test
    void aFewWordsArentEnoughForAnExtremeGrammarScore() {
        assertThat(SessionScores.grammar(List.of("I has went."), 2)).isBetween(55, 75);
        assertThat(SessionScores.grammar(List.of("Good."), 0)).isBetween(65, 80);
    }

    private static Input input(List<String> answers, List<Correction> corrections, int fillers) {
        return new Input("ROLEPLAY", "Ordering at a café", "Order two drinks", "B1", 40, null, fillers, null,
                answers.stream().map(a -> answer(a, 0)).toList(), corrections, corrections.size(), List.of(),
                CAFE.stream().map(Criterion::text).toList());
    }

    private static Input.Answer answer(String text, int fillers) {
        return new Input.Answer("What can I get you?", text, null, fillers);
    }
}
