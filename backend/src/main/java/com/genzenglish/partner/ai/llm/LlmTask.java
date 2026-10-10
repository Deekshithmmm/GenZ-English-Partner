package com.genzenglish.partner.ai.llm;

/**
 * What a model call is for. Providers use it to pick models (fast ones for Luna's side of a conversation, stronger
 * ones where judgement matters) and how long to wait for them; the mock uses it to pick a canned responder.
 */
public enum LlmTask {
    TUTOR_REPLY(true, false),
    /** A lesson's verdict and a chat's next turn wait for these notes. */
    TURN_FEEDBACK(true, true),
    SESSION_SUMMARY(false, true),
    HINT(true, false),
    EXPLAIN(false, true),
    TRANSLATE(false, true),
    INTERVIEW_PLAN(false, true),
    PLACEMENT_REACTION(true, false),
    PLACEMENT_ASSESSMENT(false, true);

    private final boolean latencySensitive;
    private final boolean judgement;

    LlmTask(boolean latencySensitive, boolean judgement) {
        this.latencySensitive = latencySensitive;
        this.judgement = judgement;
    }

    /** True when the learner is waiting on this reply in real time. */
    public boolean latencySensitive() {
        return latencySensitive;
    }

    /**
     * True for corrections, scores, levels and plans: worth the strongest model, whose quota is scarce. Luna's side of
     * the conversation (replies, hints, reactions) goes to fast models.
     */
    public boolean needsJudgement() {
        return judgement;
    }
}
