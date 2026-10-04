package com.genzenglish.partner.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.genzenglish.partner.common.Json;
import com.genzenglish.partner.practice.PracticeDtos.CriterionResult;
import com.genzenglish.partner.practice.PracticeDtos.CriterionStatus;
import com.genzenglish.partner.practice.PracticeDtos.Scorecard;
import com.genzenglish.partner.practice.PracticeDtos.ScorecardVerdict;
import com.genzenglish.partner.practice.PracticeDtos.Scores;
import com.genzenglish.partner.practice.PracticeDtos.Stats;
import com.genzenglish.partner.practice.PracticeDtos.Summary;
import com.genzenglish.partner.practice.PracticeDtos.XpItem;
import com.genzenglish.partner.practice.TurnFeedback.Correction;
import com.genzenglish.partner.practice.TurnFeedback.FillerWord;
import com.genzenglish.partner.practice.TurnFeedback.NewWord;
import com.genzenglish.partner.practice.TurnFeedback.SoundSlip;

import tools.jackson.databind.json.JsonMapper;

/** Summaries and feedback are stored as jsonb and read back on retries, so they must survive a round trip. */
class PracticeJsonTest {

    private final Json json = new Json(JsonMapper.builder().build());

    @Test
    void summaryRoundTrips() {
        Summary summary = new Summary(UUID.randomUUID(), PracticeKind.ROLEPLAY, "hr-interview", "HR interview",
                new Stats(3, 2, 48, 90, null, 1, "um"), 60,
                List.of(new XpItem("3 answers", 30), new XpItem("Spoken out loud", 10)), "Nice work. Try again.",
                List.of(new Correction("I am having", "I have", null, "\"I am having\" की जगह \"I have\" बोलिए।")),
                new Scores(72, 81, 64, 58), List.of("Use \"for\" with lengths of time"),
                new Scorecard(ScorecardVerdict.ALMOST, 1, 2, List.of(
                        new CriterionResult("Say why you want this job", CriterionStatus.MET, "I like building apps."),
                        new CriterionResult("Back up a strength with an example", CriterionStatus.PARTLY, null))));

        assertThat(json.read(json.write(summary), Summary.class)).isEqualTo(summary);
    }

    @Test
    void summariesStoredBeforeScoringStillRead() {
        String stored = """
                {"sessionId":"0190b9a4-5f1e-7c3a-9d2b-4a5e6f708192","kind":"WARM_UP","scenario":null,                "title":"Warm-up chat","stats":{"answers":2,"voiceAnswers":0,"speakingSeconds":0,"wordsSpoken":30,                "averageWpm":null,"fillerCount":0,"topFiller":null},"xp":20,"xpItems":[{"label":"2 answers","xp":20}],                "note":"Good start.","corrections":[]}""";

        Summary summary = json.read(stored, Summary.class);

        assertThat(summary.scores()).isNull();
        assertThat(summary.focus()).isEmpty();
        assertThat(summary.scorecard()).isNull();
    }

    @Test
    void feedbackRoundTripsWithAndWithoutATip() {
        TurnFeedback withTip = new TurnFeedback(List.of(new Correction("a interview", "an interview", "Vowel.")),
                "Nice!");
        assertThat(json.read(json.write(withTip), TurnFeedback.class)).isEqualTo(withTip);
        assertThat(json.read(json.write(TurnFeedback.none()), TurnFeedback.class)).isEqualTo(TurnFeedback.none());

        TurnFeedback full = new TurnFeedback(List.of(new Correction("since two years", "for two years", "Length.",
                "समय की अवधि के लिए \"for\" बोलिए।")), null, "I've been learning Java for two years.",
                List.of(new NewWord("exhausted", "extremely tired", "very tired")), List.of(new FillerWord("um", 2)),
                List.of(new SoundSlip("development", 48)));
        assertThat(json.read(json.write(full), TurnFeedback.class)).isEqualTo(full);
    }

    @Test
    void feedbackStoredBeforeTheRicherNotesStillReads() {
        TurnFeedback stored = json.read("""
                {"corrections":[{"original":"a interview","better":"an interview","why":"Vowel."}],"tip":null}""",
                TurnFeedback.class);

        assertThat(stored.corrections()).containsExactly(new Correction("a interview", "an interview", "Vowel."));
        assertThat(stored.newWords()).isEmpty();
        assertThat(stored.fillerWords()).isEmpty();
        assertThat(stored.mispronounced()).isEmpty();
    }

    @Test
    void mockResponderInputsRoundTrip() {
        TurnCoach.Input coach = new TurnCoach.Input("Why this job?", "I am having two years experience", "B1",
                "HINDI");
        assertThat(json.read(json.write(coach), TurnCoach.Input.class)).isEqualTo(coach);

        SessionSummarizer.Input summary = new SessionSummarizer.Input("ROLEPLAY", "HR interview",
                "Answer calmly and honestly.", null, 40, 120, 0, null,
                List.of(new SessionSummarizer.Input.Answer("Q?", "A.", 120, 0)),
                List.of(new Correction("I am having", "I have", null)), 1, List.of("development"),
                List.of("Say why you want this job"));
        assertThat(json.read(json.write(summary), SessionSummarizer.Input.class)).isEqualTo(summary);

        InterviewPlanner.Input plan = new InterviewPlanner.Input("B2", "Final-year B.Tech student, Java and SQL.");
        assertThat(json.read(json.write(plan), InterviewPlanner.Input.class)).isEqualTo(plan);
    }

    @Test
    void interviewPlansRoundTrip() {
        InterviewPlan plan = new InterviewPlan("Java backend developer", "Acme", List.of("Knows Spring Boot"),
                List.of("Tell me about yourself.", "Why Acme?"));
        assertThat(json.read(json.write(plan), InterviewPlan.class)).isEqualTo(plan);
    }
}
