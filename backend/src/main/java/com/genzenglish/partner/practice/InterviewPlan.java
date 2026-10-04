package com.genzenglish.partner.practice;

import java.util.List;

/**
 * A mock interview tailored to what the learner pasted. Only this plan is stored, not the résumé or job description
 * itself, so the session keeps what Luna needs and nothing more.
 *
 * @param role       the job being interviewed for, e.g. "Java backend developer"
 * @param company    the company, or null when the text doesn't name one
 * @param highlights a few short facts from the résumé or job description that Luna can follow up on
 * @param questions  the planned questions, asked one per answer
 */
public record InterviewPlan(String role, String company, List<String> highlights, List<String> questions) {

    public InterviewPlan {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        questions = questions == null ? List.of() : List.copyOf(questions);
    }

    /** "Java developer at Acme", or just the role. */
    String position() {
        return company == null ? role : role + " at " + company;
    }
}
