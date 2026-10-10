package com.genzenglish.partner.practice;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.genzenglish.partner.ai.llm.LlmClient;
import com.genzenglish.partner.ai.llm.LlmMessage;
import com.genzenglish.partner.ai.llm.LlmRequest;
import com.genzenglish.partner.ai.llm.LlmResponse;
import com.genzenglish.partner.ai.llm.LlmTask;
import com.genzenglish.partner.kai.KaiPersona;
import com.genzenglish.partner.practice.RoleplayCatalog.Roleplay;

/**
 * Luna's side of a practice conversation. The model call is latency-sensitive (the learner is waiting), so replies are
 * short; anything unusable becomes a friendly fallback so the chat never stalls.
 */
@Component
class KaiTutor {

    /** Only when no model answered: honest that Luna missed it, rather than pretending she followed. */
    static final String FALLBACK = "Sorry, I lost my train of thought for a second. Could you say that again?";
    static final String CLOSING_FALLBACK = "That was great practice. Let's stop here for today.";
    /** Also lets the mock recognise the final exchange. */
    static final String CLOSING_INSTRUCTION = "This is the last exchange.";
    /** Starts the line naming the interview question Luna asks next; the mock reads the question from it. */
    static final String NEXT_QUESTION = "Your next planned question:";
    static final String INTERVIEW_SCENE = "This is a mock HR interview";
    static final int MAX_CHARS = 480;
    private static final Logger log = LoggerFactory.getLogger(KaiTutor.class);

    private final LlmClient llm;

    KaiTutor(LlmClient llm) {
        this.llm = llm;
    }

    /** One earlier exchange: what the learner said and how Luna replied. */
    record Exchange(String learner, String kai) {
    }

    /**
     * @param scene   what kind of conversation this is ({@link #warmUpScene} or {@link #roleplayScene})
     * @param closing true when Luna should wrap the conversation up instead of asking another question
     */
    String reply(KaiPersona.LearnerContext learner, String scene, String opening, List<Exchange> history,
            String answer, boolean closing) {
        String fallback = closing ? CLOSING_FALLBACK : FALLBACK;
        try {
            return sanitize(llm.complete(request(learner, scene, opening, history, answer, closing)).text(), fallback);
        } catch (RuntimeException e) {
            log.warn("Tutor reply failed ({}); using fallback", llm.providerName(), e);
            return fallback;
        }
    }

    /**
     * The same reply, generated as a stream: {@code onText} gets Luna's cleaned-up words so far as they arrive, so the
     * app can show them before the line is finished.
     */
    String streamReply(KaiPersona.LearnerContext learner, String scene, String opening, List<Exchange> history,
            String answer, boolean closing, Consumer<String> onText) {
        String fallback = closing ? CLOSING_FALLBACK : FALLBACK;
        StringBuilder raw = new StringBuilder();
        try {
            LlmResponse response = llm.stream(request(learner, scene, opening, history, answer, closing), delta -> {
                raw.append(delta);
                String soFar = sanitizePartial(raw.toString());
                if (!soFar.isEmpty()) {
                    onText.accept(soFar);
                }
            });
            return sanitize(response.text(), fallback);
        } catch (RuntimeException e) {
            log.warn("Tutor reply stream failed ({}); using fallback", llm.providerName(), e);
            return fallback;
        }
    }

    private static LlmRequest request(KaiPersona.LearnerContext learner, String scene, String opening,
            List<Exchange> history, String answer, boolean closing) {
        String system = KaiPersona.base(learner) + "\n" + scene + "\nYou opened the conversation with: \""
                + opening + "\"\n" + (closing
                        ? CLOSING_INSTRUCTION + " Reply to what the learner just said, then bring the conversation "
                                + "to a natural close in one or two sentences. Don't ask a question."
                        : "Reply to what the learner just said, then keep the conversation going with one question.");
        // Luna's opening lives in the system prompt, so the conversation starts with the learner (some providers
        // require the first message to come from the user).
        List<LlmMessage> messages = new ArrayList<>();
        for (Exchange exchange : history) {
            messages.add(LlmMessage.user(exchange.learner()));
            messages.add(LlmMessage.assistant(exchange.kai()));
        }
        messages.add(LlmMessage.user(answer));
        return LlmRequest.text(LlmTask.TUTOR_REPLY, system, messages, 250);
    }

    static String warmUpScene(String topic) {
        return """
                This is a short warm-up chat to get the learner talking before today's practice. Today's topic: \
                "%s". Be genuinely curious about what they say and ask natural follow-up questions. Stay near the \
                topic, but it's fine to follow where the learner takes it.
                """.formatted(topic);
    }

    static String freeTalkScene(FreeTalkTopics.Topic topic) {
        String about = topic == FreeTalkTopics.OPEN ? "whatever the learner wants to talk about"
                : "\"" + topic.title() + "\"";
        return """
                This is a relaxed free chat the learner chose to have, about %s. It can run for up to ten minutes, \
                so keep it going like a good conversation between friends: react to what they say, now and then \
                share a short opinion of your own, and ask natural follow-up questions. When a thread runs dry, \
                move on to a related angle they haven't talked about yet. Follow the learner if they change the \
                subject.
                """.formatted(about);
    }

    static String interviewScene(InterviewPlan plan) {
        StringBuilder scene = new StringBuilder(INTERVIEW_SCENE).append(" for the role of ").append(plan.role());
        if (plan.company() != null) {
            scene.append(" at ").append(plan.company());
        }
        scene.append("""
                . You play a friendly but professional HR interviewer; the learner is the candidate, practising for a \
                real interview. Run it like a real first-round interview: before each question, react to the \
                candidate's answer in one short, natural sentence, the way an interviewer would, without praising \
                every answer or giving feedback. Ask the planned questions in order, one at a time. If an answer was \
                very short or vague, you may ask one follow-up about it instead of moving on. Stay in character the \
                whole time.
                """);
        if (!plan.highlights().isEmpty()) {
            scene.append("What you know from what they shared: ").append(String.join("; ", plan.highlights()))
                    .append(".\n");
        }
        scene.append("The interview plan:\n");
        for (int i = 0; i < plan.questions().size(); i++) {
            scene.append(i + 1).append(". ").append(plan.questions().get(i)).append('\n');
        }
        return scene.toString();
    }

    /** Points Luna at the planned question for its next line. */
    static String nextQuestion(String question) {
        return NEXT_QUESTION + " \"" + question + "\"";
    }

    static String roleplayScene(Roleplay roleplay) {
        return """
                This is a roleplay called "%s". You play %s; the learner plays %s. The learner's goal: %s
                Stay in character the whole time and talk the way that person really would. Keep the scene \
                realistic and moving towards the learner's goal, and add a small, realistic complication now and \
                then. Never narrate, describe actions or step out of the scene.
                """.formatted(roleplay.title(), roleplay.kaiRole(), roleplay.learnerRole(), roleplay.goal());
    }

    /** Spoken text only: no markdown, emoji, stage directions or speaker labels, and short enough to listen to. */
    static String sanitize(String text, String fallback) {
        if (text == null) {
            return fallback;
        }
        String line = clean(text);
        if (line.isEmpty()) {
            return fallback;
        }
        if (line.length() > MAX_CHARS) {
            int cut = Math.max(line.lastIndexOf(". ", MAX_CHARS), Math.max(line.lastIndexOf("? ", MAX_CHARS),
                    line.lastIndexOf("! ", MAX_CHARS)));
            line = cut > 0 ? line.substring(0, cut + 1) : fallback;
        }
        return line;
    }

    /** Luna's words so far, cleaned the same way. An action or aside that hasn't closed yet stays hidden until it does. */
    static String sanitizePartial(String text) {
        String visible = text;
        if (visible.chars().filter(c -> c == '*').count() % 2 == 1) {
            visible = visible.substring(0, visible.lastIndexOf('*'));
        }
        int bracket = visible.lastIndexOf('[');
        if (bracket > visible.lastIndexOf(']')) {
            visible = visible.substring(0, bracket);
        }
        return clean(visible);
    }

    private static String clean(String text) {
        return text
                // Bold is emphasis and keeps its words; single stars mark actions like *smiles* and go.
                .replaceAll("\\*\\*([^*]{1,200})\\*\\*", "$1")
                .replaceAll("\\*[^*]{1,80}\\*", " ")
                .replaceAll("\\[[^\\]]{1,80}]", " ")
                .replaceAll("[*_#`~]", "")
                .replaceAll("[\\p{So}\\p{Cs}\\uFE0F\\u200D]", "")
                .replaceAll("\\s+", " ")
                .strip()
                // The model sometimes starts with its name, like a script: "Luna: ..." (or her old name, Kai).
                .replaceFirst("^(?i)(?:luna|kai)\\s*:\\s*", "")
                .replaceAll("^\"|\"$", "")
                .strip();
    }
}
