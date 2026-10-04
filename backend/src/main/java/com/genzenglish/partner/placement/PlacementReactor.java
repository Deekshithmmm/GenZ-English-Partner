package com.genzenglish.partner.placement;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.genzenglish.partner.ai.llm.LlmClient;
import com.genzenglish.partner.ai.llm.LlmMessage;
import com.genzenglish.partner.ai.llm.LlmRequest;
import com.genzenglish.partner.ai.llm.LlmTask;
import com.genzenglish.partner.kai.KaiPersona;

/**
 * Luna's one-line reaction to each placement answer, so the chat feels like a conversation rather than a form.
 */
@Component
class PlacementReactor {

    static final String FALLBACK = "Thanks, that helps.";
    private static final int MAX_LENGTH = 120;
    private static final Logger log = LoggerFactory.getLogger(PlacementReactor.class);

    private final LlmClient llm;

    PlacementReactor(LlmClient llm) {
        this.llm = llm;
    }

    String react(String learnerText, KaiPersona.LearnerContext learner) {
        String system = KaiPersona.base(learner) + """

                You're running a short placement chat to find the learner's level. React to their last answer in \
                ONE short, warm sentence of at most 12 words that shows you listened. Don't ask a question (the \
                next question is added for you). Don't correct their English and don't mention levels.
                """;
        try {
            String text = llm.complete(LlmRequest.text(LlmTask.PLACEMENT_REACTION, system,
                    List.of(LlmMessage.user(learnerText)), 60)).text();
            return sanitize(text);
        } catch (RuntimeException e) {
            log.warn("Placement reaction failed ({}); using fallback", llm.providerName(), e);
            return FALLBACK;
        }
    }

    /** Keeps the reaction to one statement so it never collides with the scripted question that follows. */
    static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return FALLBACK;
        }
        String line = text.strip().replaceAll("\\s+", " ").replaceAll("^\"|\"$", "");
        int question = line.indexOf('?');
        if (question >= 0) {
            int sentenceStart = Math.max(line.lastIndexOf(". ", question), line.lastIndexOf("! ", question));
            line = sentenceStart > 0 ? line.substring(0, sentenceStart + 1) : "";
        }
        if (line.isBlank() || line.length() > MAX_LENGTH) {
            return FALLBACK;
        }
        return line;
    }
}
