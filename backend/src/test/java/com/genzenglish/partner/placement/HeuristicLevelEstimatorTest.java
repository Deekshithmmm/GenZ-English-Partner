package com.genzenglish.partner.placement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.genzenglish.partner.placement.HeuristicLevelEstimator.Sample;
import com.genzenglish.partner.profile.CefrLevel;

class HeuristicLevelEstimatorTest {

    @Test
    void oneWordAnswersLandAtTheBottom() {
        PlacementAssessment result = HeuristicLevelEstimator.estimate(List.of(
                new Sample("q1", "Ravi. Pune.", 4000L),
                new Sample("q2", "College.", 3000L),
                new Sample("q3", "Um no.", 3000L),
                new Sample("q4", "Skills good.", 3500L)));

        assertThat(result.level()).isIn(CefrLevel.A1, CefrLevel.A2);
        assertThat(result.focusAreas()).hasSize(3).contains("Stretch your answers to two or three sentences.");
    }

    @Test
    void typicalAnswersWithSomeFillersLandInTheMiddle() {
        PlacementAssessment result = HeuristicLevelEstimator.estimate(List.of(
                new Sample("q1", "Hi, my name is Arjun and I am from Mysuru. Currently I am doing my final year "
                        + "engineering in computer science.", 10_000L),
                new Sample("q2", "Basically I wake up at seven and I go to college by bus. After college I am doing "
                        + "some coding practice and, um, in night I watch one or two episodes.", 16_000L),
                new Sample("q3", "One time I missed my train to Chennai because the auto was very late. So I took a "
                        + "bus, it was so crowded, but I reached on time only.", 15_000L),
                new Sample("q4", "I think skills are more important now, because companies are asking for "
                        + "projects. But degree is also needed for the first job, like, for the filtering.",
                        16_000L)));

        assertThat(result.level()).isIn(CefrLevel.A2, CefrLevel.B1, CefrLevel.B2);
        assertThat(result.strengths()).isNotEmpty();
        assertThat(result.focusAreas()).hasSize(3);
    }

    @Test
    void fluentLinkedAnswersScoreHigherThanTypicalOnes() {
        List<Sample> fluent = List.of(
                new Sample("q1", "I'm Meera, originally from Kochi, although I've been living in Bengaluru since "
                        + "I started my undergraduate degree in economics.", 8_000L),
                new Sample("q2", "Weekdays are fairly structured: I attend lectures in the morning, then I usually "
                        + "spend the afternoon at the library, which is where I get most of my reading done.",
                        10_000L),
                new Sample("q3", "Last semester I'd planned a presentation meticulously, but the projector failed "
                        + "minutes before we started. Instead of panicking, I decided to sketch the key diagrams on "
                        + "the whiteboard, and honestly the discussion turned out better.", 14_000L),
                new Sample("q4", "I'd argue it depends on the field. Degrees still signal discipline, whereas skills "
                        + "prove you can actually deliver; however, employers increasingly value portfolios, "
                        + "so ideally you'd want both.", 12_000L));
        List<Sample> minimal = List.of(
                new Sample("q1", "My name is Ravi. I am from Pune.", 5000L),
                new Sample("q2", "I go to college. I come home.", 5000L));

        PlacementAssessment high = HeuristicLevelEstimator.estimate(fluent);
        PlacementAssessment low = HeuristicLevelEstimator.estimate(minimal);

        assertThat(high.level()).isIn(CefrLevel.B2, CefrLevel.C1);
        assertThat(high.level().compareTo(low.level())).isPositive();
        assertThat(high.fluency()).isGreaterThan(low.fluency());
        assertThat(high.vocabulary()).isGreaterThan(low.vocabulary());
    }

    @Test
    void summaryNamesTheStrongestAndWeakestAreasInsteadOfRepeatingTheLevel() {
        assertThat(HeuristicLevelEstimator.summary(83, 57, 74)).isEqualTo(
                "Fluency is your strongest area. A little practice on grammar will make the biggest difference.");
        assertThat(HeuristicLevelEstimator.summary(60, 63, 58)).contains("evenly matched");
    }

    @Test
    void scoresStayWithinBounds() {
        PlacementAssessment result = HeuristicLevelEstimator.estimate(List.of(new Sample("q", "", null)));
        assertThat(result.fluency()).isBetween(0, 100);
        assertThat(result.grammar()).isBetween(0, 100);
        assertThat(result.vocabulary()).isBetween(0, 100);
        assertThat(result.level()).isEqualTo(CefrLevel.A1);
    }
}
