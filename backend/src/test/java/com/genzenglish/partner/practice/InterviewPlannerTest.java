package com.genzenglish.partner.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Interview mode has to feel tailored even offline, and must never echo a heading back as the company or role. */
class InterviewPlannerTest {

    private static final String JOB_DESCRIPTION = """
            About The Role
            We are hiring a Java Developer to join our payments team at Zentro Labs.
            Responsibilities: build REST APIs with Spring Boot, write SQL, review code.
            Requirements: Java, Spring Boot, SQL. Internship experience is a plus.
            """;
    private static final String RESUME = """
            Priya Sharma, final-year B.Tech (CSE), Mysuru.
            Projects: built a college attendance app in React with a Node.js backend.
            Internship: two months as a data analyst intern using Python and Excel.
            """;

    @Test
    void aJobDescriptionGivesTheRoleCompanyAndSkills() {
        InterviewPlan plan = InterviewPlanner.draft(JOB_DESCRIPTION);

        assertThat(plan.role()).isEqualTo("Java developer");
        assertThat(plan.company()).isEqualTo("Zentro Labs");
        assertThat(plan.position()).isEqualTo("Java developer at Zentro Labs");
        assertThat(plan.highlights()).first().isEqualTo("The job asks for Java, Spring Boot, SQL");
        assertThat(plan.questions()).hasSize(InterviewPlanner.QUESTIONS);
        assertThat(plan.questions().get(1)).isEqualTo("What made you apply for the Java developer role at Zentro Labs?");
        assertThat(plan.questions().get(2)).isEqualTo("This role needs Java. Tell me how you've used it so far.");
    }

    @Test
    void aResumeHasNoHiringCompany() {
        InterviewPlan plan = InterviewPlanner.draft(RESUME);

        assertThat(plan.role()).isEqualTo("Data analyst");
        assertThat(plan.company()).isNull();
        assertThat(plan.highlights()).contains("Their résumé mentions React, Node.js, Python", "Has internship experience",
                "Lists projects on the résumé");
        assertThat(plan.questions().get(2)).isEqualTo("Your résumé mentions React. Tell me about a time you used it.");
    }

    @Test
    void titlesKeepTheirUsualCapitals() {
        assertThat(InterviewPlanner.draft("Looking for an HR Executive. Requirements: communication.").role())
                .isEqualTo("HR executive");
        assertThat(InterviewPlanner.draft("Role: iOS developer\nRequirements: Swift").role()).isEqualTo("iOS developer");
        assertThat(InterviewPlanner.draft("We're hiring a digital marketing executive. Must have SEO.").role())
                .isEqualTo("Digital marketing executive");
        assertThat(InterviewPlanner.draft("I enjoy building things and solving problems with friends.").role())
                .isEqualTo(InterviewPlanner.GENERIC_ROLE);

        assertThat(InterviewPlanner.inSentence("HR executive")).isEqualTo("HR executive");
        assertThat(InterviewPlanner.inSentence("iOS developer")).isEqualTo("iOS developer");
        assertThat(InterviewPlanner.inSentence("Java developer")).isEqualTo("Java developer");
        assertThat(InterviewPlanner.inSentence("Software engineer")).isEqualTo("software engineer");
    }

    @Test
    void theOpeningGreetsTheCandidateForTheRole() {
        InterviewPlan plan = InterviewPlanner.draft(JOB_DESCRIPTION);

        assertThat(PracticeService.interviewOpening("Priya", plan)).isEqualTo("Hello Priya, thanks for coming in "
                + "today for the Java developer role at Zentro Labs. Let's start with you. Tell me a little about "
                + "yourself.");
        assertThat(PracticeService.interviewOpening(null, InterviewPlanner.draft("Just a few lines about me and "
                + "what I like to do on weekends with friends."))).startsWith("Hello, thanks for coming in today. ");
    }

    @Test
    void modelPlansNeedEnoughUsableQuestions() {
        List<String> six = List.of("Tell me about yourself.", "Why us?", "Your Java project?", "Your internship?",
                "A team conflict?", "Where in three years?");
        InterviewPlan plan = InterviewPlanner.fromRaw(new InterviewPlanner.Raw("  Backend developer ", " ",
                List.of("Knows Spring Boot", "x".repeat(200)), six));

        assertThat(plan).isNotNull();
        assertThat(plan.role()).isEqualTo("Backend developer");
        assertThat(plan.company()).isNull();
        assertThat(plan.highlights()).containsExactly("Knows Spring Boot");
        assertThat(plan.questions()).containsExactlyElementsOf(six);

        assertThat(InterviewPlanner.fromRaw(new InterviewPlanner.Raw("Role", null, null, six.subList(0, 4))))
                .as("too few questions").isNull();
        assertThat(InterviewPlanner.fromRaw(new InterviewPlanner.Raw("x".repeat(80), null, null, six)).role())
                .isEqualTo(InterviewPlanner.GENERIC_ROLE);
        assertThat(InterviewPlanner.fromRaw(new InterviewPlanner.Raw(null, null, null,
                Collections.nCopies(6, "Same question?")))).as("duplicates collapse").isNull();
        assertThat(InterviewPlanner.fromRaw(null)).isNull();
    }
}
