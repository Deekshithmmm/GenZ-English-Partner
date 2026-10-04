package com.genzenglish.partner.profile;

/**
 * Luna's two original voices, both a young woman's: soft by default, or brighter. Providers map these to their own
 * female voice IDs (for example a soft Indian-English neural voice for {@link #KAI_WARM}).
 */
public enum TutorVoice {
    KAI_WARM("Soft", "Gentle and calm, a little slower."),
    KAI_BRIGHT("Bright", "Cheerful and clear, a little livelier.");

    private final String label;
    private final String description;

    TutorVoice(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
