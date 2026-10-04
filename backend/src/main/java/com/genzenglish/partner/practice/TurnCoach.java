package com.genzenglish.partner.practice;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.genzenglish.partner.ai.llm.LlmClient;
import com.genzenglish.partner.ai.llm.LlmMessage;
import com.genzenglish.partner.ai.llm.LlmRequest;
import com.genzenglish.partner.ai.llm.LlmResponse;
import com.genzenglish.partner.ai.llm.LlmTask;
import com.genzenglish.partner.common.Json;
import com.genzenglish.partner.profile.CefrLevel;
import com.genzenglish.partner.profile.ExplanationLanguage;

/**
 * Finds the one or two slips in an answer that are worth a note, explains them in the learner's own language, and
 * suggests a smoother way to say the whole thing. Uses the model when it can and falls back to {@link CommonSlips},
 * so the learner always gets feedback even when the model is down.
 */
@Component
class TurnCoach {

    static final String SYSTEM_PROMPT = """
            You are the feedback coach inside an English speaking-practice app for Indian learners aged 18 to 26.
            You receive the question the learner was answering, what they said, their level and the language they \
            want explanations in. Their words come from speech recognition, so ignore punctuation, capitalisation, \
            spelling and filler words.
            Pick at most two mistakes that matter most for sounding natural and correct when speaking: grammar, \
            word choice, or Indian-English patterns that confuse listeners elsewhere (for example "I am having two \
            years experience", "myself Arjun", "since two years", "discuss about"). Ignore accent and tiny slips. \
            Widely understood Indian English is fine unless it would sound wrong in an interview.

            Reply with only one JSON object, no other text:
            {"corrections":[{"original":"the learner's exact words","better":"a natural way to say it",\
            "why":"one short, friendly reason","whyNative":"the same reason in the explanation language"}],\
            "betterPhrasing":"","newWords":[{"word":"","meaning":"","instead":""}],"tip":""}
            Copy "original" exactly from what the learner said. Keep "why" under 20 words, in simple English for the \
            learner's level.
            "whyNative": the same reason, written the way a friendly tutor would say it in the explanation language \
            and its own script, with the English words kept in English inside quotes. Leave it empty when the \
            explanation language is ENGLISH.
            "betterPhrasing": the learner's whole answer the way a fluent speaker would say it, keeping their meaning, \
            their ideas and roughly their length. Leave it empty if the answer already sounds natural.
            "newWords": at most one word or short phrase that would make this answer stronger, with a simple meaning \
            and "instead" quoting the learner's words it would replace (or empty). Suggest nothing rare or too formal \
            for their level; an empty list is fine.
            Use "tip" for one short, encouraging pointer, or leave it empty. If nothing is worth correcting, return \
            an empty "corrections" list.
            """;
    private static final Logger log = LoggerFactory.getLogger(TurnCoach.class);

    private final LlmClient llm;
    private final Json json;

    TurnCoach(LlmClient llm, Json json) {
        this.llm = llm;
        this.json = json;
    }

    /**
     * The user message sent to the model; also parsed by the mock responder.
     *
     * @param explanationLanguage an {@link ExplanationLanguage} name, e.g. {@code HINDI}
     */
    record Input(String question, String answer, String level, String explanationLanguage) {
    }

    TurnFeedback review(String question, String answer, CefrLevel level, ExplanationLanguage language) {
        ExplanationLanguage explainIn = language == null ? ExplanationLanguage.ENGLISH : language;
        try {
            String input = json.write(new Input(question, answer, level == null ? null : level.name(),
                    explainIn.name()));
            LlmResponse response = llm.complete(LlmRequest.json(LlmTask.TURN_FEEDBACK, SYSTEM_PROMPT,
                    List.of(LlmMessage.user(input)), 700));
            TurnFeedback feedback = TurnFeedback.fromRaw(json.readLenient(response.text(), TurnFeedback.Raw.class),
                    answer, explainIn);
            if (feedback != null) {
                return feedback.withNativeFallback(explainIn);
            }
            log.warn("Turn feedback JSON was unusable; using built-in rules");
        } catch (RuntimeException e) {
            log.warn("Turn feedback failed ({}); using built-in rules", llm.providerName(), e);
        }
        return CommonSlips.review(answer).withNativeFallback(explainIn);
    }
}
