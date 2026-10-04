package com.genzenglish.partner.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.genzenglish.partner.practice.PracticeDtos.CriterionResult;
import com.genzenglish.partner.practice.PracticeDtos.CriterionStatus;
import com.genzenglish.partner.practice.PracticeDtos.ScorecardVerdict;
import com.genzenglish.partner.practice.SuccessCriteria.Criterion;

/** The offline scorecard must quote the learner fairly: met only on a real cue, with their own sentence as proof. */
class SuccessCriteriaTest {

    private static final List<Criterion> CAFE = RoleplayCatalog.find("ordering-at-cafe").orElseThrow().criteria();

    @Test
    void aCriterionIsMetBySentenceAndQuotesTheLearner() {
        List<CriterionResult> results = SuccessCriteria.evaluate(CAFE, List.of(
                "Hi! Can I get one cold coffee and one masala chai? And what is there in the veg sandwich?",
                "Okay, thanks."));

        assertThat(results).extracting(CriterionResult::status)
                .containsExactly(CriterionStatus.MET, CriterionStatus.MET, CriterionStatus.MISSED);
        assertThat(results.getFirst().evidence()).isEqualTo("Can I get one cold coffee and one masala chai?");
        assertThat(results.get(1).evidence()).isEqualTo("And what is there in the veg sandwich?");
        assertThat(results.getLast().evidence()).isNull();
        assertThat(results.getFirst().criterion()).isEqualTo("Order your drinks and something to eat");
    }

    @Test
    void cuesMatchWholeWordsOnly() {
        Criterion pay = Criterion.of("Pay", "pay|card");
        assertThat(SuccessCriteria.evaluate(List.of(pay), List.of("The payment was by cardboard box.")))
                .extracting(CriterionResult::status).containsExactly(CriterionStatus.MISSED);
        assertThat(SuccessCriteria.evaluate(List.of(pay), List.of("Can I pay by UPI?")))
                .extracting(CriterionResult::status).containsExactly(CriterionStatus.MET);
    }

    @Test
    void cuesIgnoreCaseAndPunctuation() {
        Criterion order = Criterion.of("Give your order details", "order number|\\d{3,}");
        assertThat(SuccessCriteria.evaluate(List.of(order), List.of("My ORDER-NUMBER is 4471.")))
                .extracting(CriterionResult::status).containsExactly(CriterionStatus.MET);
    }

    @Test
    void longEvidenceIsShortened() {
        String rambling = "I would like to order " + "a really large cold coffee ".repeat(10) + "please.";
        String evidence = SuccessCriteria.evaluate(CAFE.subList(0, 1), List.of(rambling)).getFirst().evidence();

        assertThat(evidence).hasSizeLessThanOrEqualTo(SuccessCriteria.MAX_EVIDENCE).startsWith("I would like")
                .endsWith("…");
    }

    @Test
    void verdictCountsPartlyAsHalf() {
        assertThat(SuccessCriteria.scorecard(results(CriterionStatus.MET, CriterionStatus.MET, CriterionStatus.MET))
                .verdict()).isEqualTo(ScorecardVerdict.REACHED);
        assertThat(SuccessCriteria.scorecard(results(CriterionStatus.MET, CriterionStatus.PARTLY,
                CriterionStatus.MISSED)).verdict()).isEqualTo(ScorecardVerdict.ALMOST);
        assertThat(SuccessCriteria.scorecard(results(CriterionStatus.PARTLY, CriterionStatus.MISSED,
                CriterionStatus.MISSED)).verdict()).isEqualTo(ScorecardVerdict.NOT_YET);

        PracticeDtos.Scorecard twoOfFour = SuccessCriteria.scorecard(results(CriterionStatus.MET, CriterionStatus.MET,
                CriterionStatus.MISSED, CriterionStatus.MISSED));
        assertThat(twoOfFour.verdict()).isEqualTo(ScorecardVerdict.ALMOST);
        assertThat(twoOfFour.met()).isEqualTo(2);
        assertThat(twoOfFour.total()).isEqualTo(4);
    }

    @Test
    void everyRoleplayHasAGoalAndThreeCriteria() {
        assertThat(RoleplayCatalog.all()).hasSize(12).allSatisfy(roleplay -> {
            assertThat(roleplay.goal()).isNotBlank();
            assertThat(roleplay.kaiRole()).isNotBlank();
            assertThat(roleplay.criteria()).hasSize(3);
        });
        assertThat(InterviewPlanner.CRITERIA).hasSize(4);
    }

    private static List<CriterionResult> results(CriterionStatus... statuses) {
        return java.util.Arrays.stream(statuses).map(s -> new CriterionResult("Do it", s, null)).toList();
    }
}
