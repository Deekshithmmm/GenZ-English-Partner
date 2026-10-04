package com.genzenglish.partner.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.genzenglish.partner.practice.PracticeDtos.XpItem;
import com.genzenglish.partner.practice.TurnFeedback.Correction;

class PracticeSummaryTest {

    @Test
    void xpRewardsAnswersSpeakingAndFinishing() {
        PracticeRewards.Reward finished = PracticeRewards.of(new SessionStats(4, 3, 70, 120, 110, 2, "um"), 4);
        assertThat(finished.items()).containsExactly(new XpItem("4 answers", 40), new XpItem("Spoken out loud", 15),
                new XpItem("Finished the session", 20));
        assertThat(finished.total()).isEqualTo(75);

        PracticeRewards.Reward early = PracticeRewards.of(new SessionStats(1, 0, 0, 12, null, 0, null), 4);
        assertThat(early.items()).containsExactly(new XpItem("1 answer", 10));
        assertThat(early.total()).isEqualTo(10);
    }

    @Test
    void fallbackNotePraisesSpeakingTimeAndSuggestsTheFirstCorrection() {
        String note = SessionSummarizer.fallbackNote(input(65, "um", List.of(answer("I am having two years", 1)),
                List.of(new Correction("I am having", "I have", "Not -ing here."))));

        assertThat(note).isEqualTo("You spoke for 1 minute 5 seconds across 1 answer, and that's real practice. "
                + "Next time, try \"I have\" instead of \"I am having\".");
    }

    @Test
    void fallbackNoteFallsBackToFillersThenLength() {
        String fillers = SessionSummarizer.fallbackNote(input(10, "basically",
                List.of(answer("basically it was basically fine", 2)), List.of()));
        assertThat(fillers).isEqualTo("You answered 1 time and kept the conversation moving. "
                + "Next time, swap \"basically\" for a short, calm pause.");

        String shortAnswers = SessionSummarizer.fallbackNote(input(10, null, List.of(answer("Yes, fine.", 0)),
                List.of()));
        assertThat(shortAnswers).endsWith("Next time, stretch each answer to two or three sentences.");
    }

    @Test
    void durationsReadNaturally() {
        assertThat(SessionSummarizer.duration(45)).isEqualTo("45 seconds");
        assertThat(SessionSummarizer.duration(60)).isEqualTo("1 minute");
        assertThat(SessionSummarizer.duration(125)).isEqualTo("2 minutes 5 seconds");
    }

    @Test
    void warmUpOpeningGreetsByNameAndAsksInKaisVoice() {
        assertThat(PracticeService.warmUpOpening("Priya", "Tell Luna about the last show you couldn't stop watching."))
                .isEqualTo("Hey Priya, let's warm up. Tell me about the last show you couldn't stop watching.");
        assertThat(PracticeService.warmUpOpening(null, "What's a small win from this week?"))
                .isEqualTo("Hey, let's warm up. What's a small win from this week?");
    }

    private static SessionSummarizer.Input input(int seconds, String topFiller, List<SessionSummarizer.Input.Answer> answers,
            List<Correction> corrections) {
        int fillers = answers.stream().mapToInt(SessionSummarizer.Input.Answer::fillerCount).sum();
        return new SessionSummarizer.Input("WARM_UP", "Warm-up chat", "A small win", "B1", seconds, null, fillers,
                topFiller, answers, corrections, corrections.size(), List.of(), List.of());
    }

    private static SessionSummarizer.Input.Answer answer(String text, int fillers) {
        return new SessionSummarizer.Input.Answer("What did you do?", text, null, fillers);
    }
}
