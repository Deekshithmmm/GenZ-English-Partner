package com.genzenglish.partner.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** A free chat runs on time: Luna keeps it going for ten minutes and wraps up as they run out. */
class FreeTalkTimeTest {

    private static final Instant START = Instant.parse("2026-10-10T10:00:00Z");
    private final PracticeService.Scene freeTalk = PracticeService.freeTalk(FreeTalkTopics.OPEN, Duration.ofMinutes(10));

    @Test
    void lunaKeepsTalkingUntilTheTimeIsNearlyUp() {
        assertThat(freeTalk.timeLimit()).isEqualTo(Duration.ofMinutes(10));
        assertThat(freeTalk.wrapsUpAfter(1, START, START.plusSeconds(20))).isFalse();
        // Long after the old twenty answers, the chat still goes on while there's time.
        assertThat(freeTalk.wrapsUpAfter(25, START, START.plus(Duration.ofMinutes(8)))).isFalse();
        assertThat(freeTalk.wrapsUpAfter(14, START, START.plus(Duration.ofMinutes(9)).plusSeconds(29))).isFalse();
    }

    @Test
    void theFirstAnswerInTheLastHalfMinuteIsTheLast() {
        Instant nearlyUp = START.plus(Duration.ofMinutes(10)).minus(PracticeService.WRAP_UP_BEFORE_THE_END);
        assertThat(freeTalk.wrapsUpAfter(14, START, nearlyUp)).isTrue();
        // An answer that arrives after the time is up still gets Luna's goodbye.
        assertThat(freeTalk.wrapsUpAfter(15, START, START.plus(Duration.ofMinutes(12)))).isTrue();
    }

    @Test
    void aVeryQuickChatStillStopsAtTheMostAnswersOneSceneTakes() {
        assertThat(freeTalk.targetTurns()).isEqualTo(PracticeDtos.MAX_TURNS);
        assertThat(freeTalk.wrapsUpAfter(PracticeDtos.MAX_TURNS, START, START.plusSeconds(300))).isTrue();
    }

    @Test
    void chatsThatEndAfterSomeAnswersIgnoreTheClock() {
        PracticeService.Scene answers = new PracticeService.Scene(PracticeKind.WARM_UP, null, "Warm-up chat", "Food",
                6, "", java.util.List.of(), java.util.List.of(), null);

        assertThat(answers.wrapsUpAfter(5, START, START.plus(Duration.ofHours(1)))).isFalse();
        assertThat(answers.wrapsUpAfter(6, START, START.plusSeconds(30))).isTrue();
    }
}
