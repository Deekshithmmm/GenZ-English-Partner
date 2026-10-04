package com.genzenglish.partner.ai.llm;

/**
 * What a model call is for. Providers use it to pick a model tier (fast for live replies, stronger for
 * assessments); the mock uses it to pick a canned responder.
 */
public enum LlmTask {
    TUTOR_REPLY(true),
    TURN_FEEDBACK(false),
    SESSION_SUMMARY(false),
    HINT(true),
    EXPLAIN(false),
    TRANSLATE(false),
    INTERVIEW_PLAN(false),
    PLACEMENT_REACTION(true),
    PLACEMENT_ASSESSMENT(false);

    private final boolean latencySensitive;

    LlmTask(boolean latencySensitive) {
        this.latencySensitive = latencySensitive;
    }

    /** True when the learner is waiting on this reply in real time. */
    public boolean latencySensitive() {
        return latencySensitive;
    }
}
