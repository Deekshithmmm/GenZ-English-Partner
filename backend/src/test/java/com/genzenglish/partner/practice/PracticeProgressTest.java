package com.genzenglish.partner.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class PracticeProgressTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    @Test
    void noPracticeMeansNoStreak() {
        assertThat(PracticeProgress.streak(List.of(), TODAY)).isEqualTo(new PracticeProgress.Streak(0, 0));
    }

    @Test
    void consecutiveDaysEndingTodayCount() {
        PracticeProgress.Streak streak = PracticeProgress.streak(days(0, 1, 2), TODAY);
        assertThat(streak).isEqualTo(new PracticeProgress.Streak(3, 3));
    }

    @Test
    void aStreakStaysAliveUntilTheEndOfTheNextDay() {
        assertThat(PracticeProgress.streak(days(1, 2), TODAY).current()).isEqualTo(2);
        assertThat(PracticeProgress.streak(days(2, 3), TODAY).current()).isZero();
    }

    @Test
    void longestRemembersAnOlderRun() {
        PracticeProgress.Streak streak = PracticeProgress.streak(days(0, 1, 5, 6, 7, 8), TODAY);
        assertThat(streak).isEqualTo(new PracticeProgress.Streak(2, 4));
    }

    @Test
    void aDayAheadOfTodayAfterATimeZoneChangeStillCounts() {
        assertThat(PracticeProgress.streak(days(-1, 0, 1), TODAY).current()).isEqualTo(3);
    }

    @Test
    void weekRunsMondayToSundayAroundToday() {
        // TODAY is Thursday 1 October 2026: practice on Monday and today, plus last Sunday (a different week).
        assertThat(PracticeProgress.week(days(0, 3, 4), TODAY))
                .containsExactly(true, false, false, true, false, false, false);
    }

    /** Practice days, given as days before {@link #TODAY}, newest first. */
    private static List<LocalDate> days(int... daysAgo) {
        return java.util.Arrays.stream(daysAgo).mapToObj(TODAY::minusDays).toList();
    }
}
