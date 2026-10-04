package com.genzenglish.partner.practice;

/**
 * The kinds of practice chat a learner can start: from today's plan, a roleplay, free talk with Luna, or a mock interview
 * built from their résumé or a job description.
 */
public enum PracticeKind {
    WARM_UP(SessionType.WARM_UP, 4),
    ROLEPLAY(SessionType.ROLEPLAY, 6),
    FREE_TALK(SessionType.FREE_TALK, 8),
    INTERVIEW(SessionType.INTERVIEW, InterviewPlanner.QUESTIONS);

    private final SessionType sessionType;
    private final int defaultTargetTurns;

    PracticeKind(SessionType sessionType, int defaultTargetTurns) {
        this.sessionType = sessionType;
        this.defaultTargetTurns = defaultTargetTurns;
    }

    public SessionType sessionType() {
        return sessionType;
    }

    /** Learner answers before Luna wraps up; roleplays can set their own. */
    public int defaultTargetTurns() {
        return defaultTargetTurns;
    }

    public static PracticeKind of(SessionType type) {
        for (PracticeKind kind : values()) {
            if (kind.sessionType == type) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Not a practice session: " + type);
    }
}
