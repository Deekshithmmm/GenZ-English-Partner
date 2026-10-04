package com.genzenglish.partner.placement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.genzenglish.partner.profile.CefrLevel;

/** Model output is untrusted: these cover how we validate and clean it. */
class PlacementModelOutputTest {

    @Test
    void acceptsWellFormedAssessmentAndClampsScores() {
        PlacementAssessment result = PlacementAssessment.fromRaw(new PlacementAssessment.Raw("b1", 140, 55, -3,
                List.of("Clear answers"), List.of(" Use past tense ", "Link ideas", "Cut fillers", "Extra"), " Nice! "));

        assertThat(result).isNotNull();
        assertThat(result.level()).isEqualTo(CefrLevel.B1);
        assertThat(result.fluency()).isEqualTo(100);
        assertThat(result.vocabulary()).isZero();
        assertThat(result.focusAreas()).containsExactly("Use past tense", "Link ideas", "Cut fillers");
        assertThat(result.summary()).isEqualTo("Nice!");
    }

    @Test
    void rejectsUnknownLevelsOrMissingScores() {
        assertThat(PlacementAssessment.fromRaw(new PlacementAssessment.Raw("C2", 50, 50, 50, List.of(),
                List.of("x"), "s"))).isNull();
        assertThat(PlacementAssessment.fromRaw(new PlacementAssessment.Raw("B1", null, 50, 50, List.of(),
                List.of("x"), "s"))).isNull();
        assertThat(PlacementAssessment.fromRaw(new PlacementAssessment.Raw("B1", 50, 50, 50, List.of(),
                List.of(), "s"))).isNull();
    }

    @Test
    void reactionIsTrimmedToAStatementWithoutQuestions() {
        assertThat(PlacementReactor.sanitize("  \"Mysuru, nice!\"  ")).isEqualTo("Mysuru, nice!");
        assertThat(PlacementReactor.sanitize("Nice one. What else do you do?")).isEqualTo("Nice one.");
        assertThat(PlacementReactor.sanitize("What else?")).isEqualTo(PlacementReactor.FALLBACK);
        assertThat(PlacementReactor.sanitize("")).isEqualTo(PlacementReactor.FALLBACK);
        assertThat(PlacementReactor.sanitize("a".repeat(200))).isEqualTo(PlacementReactor.FALLBACK);
    }
}
