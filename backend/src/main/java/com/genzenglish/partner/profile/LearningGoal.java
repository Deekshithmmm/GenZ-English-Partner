package com.genzenglish.partner.profile;

public enum LearningGoal {
    JOB_INTERVIEWS("Job interviews"),
    COLLEGE_PRESENTATIONS("College and presentations"),
    EVERYDAY_CONFIDENCE("Everyday confidence"),
    CASUAL_SOCIAL("Casual and social");

    private final String label;

    LearningGoal(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
