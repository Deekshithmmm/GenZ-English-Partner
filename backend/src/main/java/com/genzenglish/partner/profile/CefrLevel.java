package com.genzenglish.partner.profile;

/** Speaking level on the CEFR scale, with plain descriptions a learner can recognise themselves in. */
public enum CefrLevel {
    A1("Starter", "You can say simple things about yourself with short, familiar phrases."),
    A2("Basic", "You can handle everyday topics in short sentences when people speak slowly."),
    B1("Independent", "You can talk about your life, plans and opinions, even if you pause to find words."),
    B2("Confident", "You can hold a real discussion and explain your view with reasons."),
    C1("Advanced", "You speak fluently and flexibly, and can adjust your tone for work or friends.");

    private final String label;
    private final String description;

    CefrLevel(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean atMost(CefrLevel other) {
        return compareTo(other) <= 0;
    }

    public boolean atLeast(CefrLevel other) {
        return compareTo(other) >= 0;
    }
}
