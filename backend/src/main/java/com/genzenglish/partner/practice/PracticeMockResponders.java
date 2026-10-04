package com.genzenglish.partner.practice;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.genzenglish.partner.ai.llm.LlmRequest;
import com.genzenglish.partner.ai.llm.LlmTask;
import com.genzenglish.partner.ai.llm.MockLlmResponder;
import com.genzenglish.partner.common.Json;
import com.genzenglish.partner.profile.ExplanationLanguage;

/** What the mock LLM says during practice. Feedback, notes and plans come from the same rules the real model falls back to. */
@Configuration
@ConditionalOnProperty(prefix = "app.ai.llm", name = "provider", havingValue = "mock", matchIfMissing = true)
class PracticeMockResponders {

    private static final List<String> WARM_UP_REPLIES = List.of(
            "Oh nice, I like that. What made you think of it?",
            "That sounds fun. How did it make you feel?",
            "Ha, fair enough. What happened next?",
            "Interesting! Would you do it again?",
            "I get that. Who were you with?");
    private static final List<String> ROLEPLAY_REPLIES = List.of(
            "Okay, got it. Could you tell me a little more about that?",
            "Right, that makes sense. And what would you like to do next?",
            "I see. Can you give me one example?",
            "Sure. Is there anything else I should know?",
            "Alright. How would you like to go ahead?");
    private static final List<String> INTERVIEW_REACTIONS = List.of(
            "Thanks, that's helpful.",
            "Okay, good to know.",
            "Got it, thank you.",
            "Right, that makes sense.");
    private static final String CLOSING = "That was really nice talking with you. Let's wrap up here for today.";
    private static final String INTERVIEW_CLOSING = "Thank you, that's all from my side. It was good talking to you, "
            + "and we'll be in touch soon.";

    @Bean
    MockLlmResponder tutorReplyMock() {
        return new MockLlmResponder() {
            @Override
            public LlmTask task() {
                return LlmTask.TUTOR_REPLY;
            }

            @Override
            public String respond(LlmRequest request) {
                String system = request.system();
                // Vary by turn as well as by answer, so the same typed answer twice doesn't get the same reply.
                int pick = request.lastUserMessage().hashCode() + request.messages().size();
                if (system.contains(KaiTutor.CLOSING_INSTRUCTION)) {
                    return system.contains(KaiTutor.INTERVIEW_SCENE) ? INTERVIEW_CLOSING : CLOSING;
                }
                int next = system.indexOf(KaiTutor.NEXT_QUESTION);
                if (next >= 0) {
                    String rest = system.substring(next + KaiTutor.NEXT_QUESTION.length());
                    int lineEnd = rest.indexOf('\n');
                    String question = (lineEnd < 0 ? rest : rest.substring(0, lineEnd)).strip()
                            .replaceAll("^\"|\"$", "");
                    return INTERVIEW_REACTIONS.get(Math.floorMod(pick, INTERVIEW_REACTIONS.size())) + " " + question;
                }
                List<String> replies = system.contains("This is a roleplay") ? ROLEPLAY_REPLIES : WARM_UP_REPLIES;
                return replies.get(Math.floorMod(pick, replies.size()));
            }
        };
    }

    @Bean
    MockLlmResponder turnFeedbackMock(Json json) {
        return new MockLlmResponder() {
            @Override
            public LlmTask task() {
                return LlmTask.TURN_FEEDBACK;
            }

            @Override
            public String respond(LlmRequest request) {
                TurnCoach.Input input = json.read(request.lastUserMessage(), TurnCoach.Input.class);
                ExplanationLanguage language = input.explanationLanguage() == null ? ExplanationLanguage.ENGLISH
                        : ExplanationLanguage.valueOf(input.explanationLanguage());
                TurnFeedback feedback = CommonSlips.review(input.answer()).withNativeFallback(language);
                return json.write(new TurnFeedback.Raw(
                        feedback.corrections().stream()
                                .map(c -> new TurnFeedback.RawCorrection(c.original(), c.better(), c.why(),
                                        c.whyNative()))
                                .toList(),
                        "", feedback.betterPhrasing(),
                        feedback.newWords().stream()
                                .map(w -> new TurnFeedback.RawNewWord(w.word(), w.meaning(), w.instead()))
                                .toList()));
            }
        };
    }

    @Bean
    MockLlmResponder hintMock(Json json) {
        return new MockLlmResponder() {
            @Override
            public LlmTask task() {
                return LlmTask.HINT;
            }

            @Override
            public String respond(LlmRequest request) {
                KaiHinter.Input input = json.read(request.lastUserMessage(), KaiHinter.Input.class);
                return json.write(new KaiHinter.Raw(KaiHinter.starters(input.kaiLine())));
            }
        };
    }

    /** The note and scores; focus and criteria are left out, so the service's rules (which know the cues) fill them. */
    @Bean
    MockLlmResponder sessionSummaryMock(Json json) {
        return new MockLlmResponder() {
            @Override
            public LlmTask task() {
                return LlmTask.SESSION_SUMMARY;
            }

            @Override
            public String respond(LlmRequest request) {
                SessionSummarizer.Input input = json.read(request.lastUserMessage(), SessionSummarizer.Input.class);
                List<String> answers = input.answerTexts();
                return json.write(new SessionSummarizer.Raw(SessionSummarizer.fallbackNote(input),
                        new SessionSummarizer.RawScores(
                                SessionScores.fluency(answers, input.averageWpm(), input.fillerCount()),
                                SessionScores.grammar(answers, input.correctionCount()),
                                SessionScores.vocabulary(answers)),
                        null, null));
            }
        };
    }

    @Bean
    MockLlmResponder interviewPlanMock(Json json) {
        return new MockLlmResponder() {
            @Override
            public LlmTask task() {
                return LlmTask.INTERVIEW_PLAN;
            }

            @Override
            public String respond(LlmRequest request) {
                InterviewPlanner.Input input = json.read(request.lastUserMessage(), InterviewPlanner.Input.class);
                InterviewPlan plan = InterviewPlanner.draft(input.pasted());
                return json.write(new InterviewPlanner.Raw(plan.role(), plan.company(), plan.highlights(),
                        plan.questions()));
            }
        };
    }
}
