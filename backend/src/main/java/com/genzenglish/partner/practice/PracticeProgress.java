package com.genzenglish.partner.practice;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Streaks, XP and what's done today, all read from completed sessions. A day counts once any practice was completed
 * on the learner's local date, and a streak stays alive until the end of the day after the last practice.
 */
@Service
public class PracticeProgress {

    private final PracticeSessionRepository sessions;

    public PracticeProgress(PracticeSessionRepository sessions) {
        this.sessions = sessions;
    }

    /**
     * @param roleplaysDone   slugs of the roleplays completed today
     * @param lastLessonToday the grammar lesson finished most recently today, or null if none was
     * @param week            whether the learner practised on each day of this week, Monday first
     */
    public record Snapshot(int currentStreak, int longestStreak, int xpToday, int xpTotal, boolean warmUpDone,
            Set<String> roleplaysDone, String lastLessonToday, List<Boolean> week) {

        /** A grammar lesson was finished today. */
        public boolean lessonDone() {
            return lastLessonToday != null;
        }
    }

    record Streak(int current, int longest) {
    }

    @Transactional(readOnly = true)
    public Snapshot of(UUID userId, LocalDate today) {
        List<PracticeSession> done = sessions.findByUserIdAndStatusAndLocalDate(userId, SessionStatus.COMPLETED, today);
        List<LocalDate> days = sessions.findPracticeDays(userId, SessionStatus.COMPLETED);
        Streak streak = streak(days, today);
        long total = sessions.sumXp(userId, SessionStatus.COMPLETED);
        return new Snapshot(streak.current(), streak.longest(),
                done.stream().mapToInt(PracticeSession::getXp).sum(),
                (int) Math.min(total, Integer.MAX_VALUE),
                done.stream().anyMatch(s -> s.getType() == SessionType.WARM_UP),
                done.stream()
                        .filter(s -> s.getType() == SessionType.ROLEPLAY && s.getScenario() != null)
                        .map(PracticeSession::getScenario)
                        .collect(Collectors.toUnmodifiableSet()),
                done.stream()
                        .filter(s -> s.getType() == SessionType.LESSON && s.getScenario() != null)
                        .max(Comparator.comparing(PracticeSession::getEndedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .map(PracticeSession::getScenario)
                        .orElse(null),
                week(days, today));
    }

    /** Monday to Sunday of the week {@code today} falls in. */
    static List<Boolean> week(List<LocalDate> days, LocalDate today) {
        Set<LocalDate> practised = new HashSet<>(days);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return IntStream.range(0, 7)
                .mapToObj(i -> practised.contains(monday.plusDays(i)))
                .toList();
    }

    /**
     * @param days distinct practice days, newest first. A day after {@code today} (the learner moved to an earlier
     *             time zone) still counts as recent.
     */
    static Streak streak(List<LocalDate> days, LocalDate today) {
        int current = 0;
        int longest = 0;
        int run = 0;
        boolean inFirstRun = !days.isEmpty() && !days.getFirst().isBefore(today.minusDays(1));
        LocalDate previous = null;
        for (LocalDate day : days) {
            boolean continues = previous != null && day.equals(previous.minusDays(1));
            if (!continues && previous != null) {
                inFirstRun = false;
            }
            run = continues ? run + 1 : 1;
            longest = Math.max(longest, run);
            if (inFirstRun) {
                current = run;
            }
            previous = day;
        }
        return new Streak(current, longest);
    }
}
