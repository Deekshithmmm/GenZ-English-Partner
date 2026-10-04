package com.genzenglish.partner.path;

import java.time.LocalDate;
import java.util.List;

import com.genzenglish.partner.profile.ProfileDtos.LevelInfo;

/**
 * The home screen: one plan for today, plus streak and XP.
 *
 * @param action what the app opens for a plan item, e.g. {@code warmup}, {@code lesson}, {@code roleplay:hr-interview}
 *               or {@code words}
 */
public record TodayResponse(
        LocalDate date,
        int dailyMinutes,
        LevelInfo level,
        FocusUnit focus,
        List<PlanItem> plan,
        Streak streak,
        Xp xp) {

    public enum PlanKind {
        WARM_UP,
        MICRO_LESSON,
        ROLEPLAY,
        WORD_REVIEW
    }

    public record FocusUnit(String id, String title, String summary) {
    }

    public record PlanItem(String id, PlanKind kind, String title, String subtitle, int minutes, String action,
            boolean done) {

        public PlanItem withDone(boolean done) {
            return new PlanItem(id, kind, title, subtitle, minutes, action, done);
        }
    }

    /**
     * @param week whether the learner practised on each day of this week, Monday first
     */
    public record Streak(int current, int longest, boolean freezeAvailable, List<Boolean> week) {
    }

    public record Xp(int today, int total) {
    }
}
