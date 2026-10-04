package com.genzenglish.partner.practice;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.genzenglish.partner.ai.llm.LlmClient;
import com.genzenglish.partner.ai.llm.LlmMessage;
import com.genzenglish.partner.ai.llm.LlmRequest;
import com.genzenglish.partner.ai.llm.LlmTask;
import com.genzenglish.partner.common.Json;

/**
 * "What could I say next?": two ideas for a learner who's stuck. Uses the model when it can and falls back to
 * sentence starters, so the hint button always gives the learner something to say.
 */
@Component
class KaiHinter {

    static final String SYSTEM_PROMPT = """
            You help a learner who is stuck in a spoken English practice chat with Luna, an AI speaking partner. You \
            receive the scene, the conversation so far and Luna's latest line.
            Suggest two different things the learner could say next, as the learner, in the first person. Each idea \
            is one natural spoken sentence of at most 15 words that answers Luna and leaves room to say more. Match \
            the learner's level: simpler words for A1 and A2. Plain English, no emojis.
            Reply with only one JSON object: {"ideas":["...","..."]}
            """;
    static final int MAX_IDEAS = 2;
    static final int MAX_IDEA_CHARS = 140;
    private static final Logger log = LoggerFactory.getLogger(KaiHinter.class);

    private final LlmClient llm;
    private final Json json;

    KaiHinter(LlmClient llm, Json json) {
        this.llm = llm;
        this.json = json;
    }

    /** The user message sent to the model; also parsed by the mock responder. */
    record Input(String scene, List<KaiTutor.Exchange> conversation, String kaiLine, String level) {
    }

    record Raw(List<String> ideas) {
    }

    List<String> ideas(Input input) {
        try {
            String text = llm.complete(LlmRequest.json(LlmTask.HINT, SYSTEM_PROMPT,
                    List.of(LlmMessage.user(json.write(input))), 150)).text();
            List<String> ideas = clean(json.readLenient(text, Raw.class));
            if (!ideas.isEmpty()) {
                return ideas;
            }
            log.warn("Hint JSON was unusable; using sentence starters");
        } catch (RuntimeException e) {
            log.warn("Hint failed ({}); using sentence starters", llm.providerName(), e);
        }
        return starters(input.kaiLine());
    }

    static List<String> clean(Raw raw) {
        if (raw == null || raw.ideas() == null) {
            return List.of();
        }
        return raw.ideas().stream()
                .filter(idea -> idea != null && !idea.isBlank())
                .map(idea -> idea.strip().replaceAll("^[\"“]|[\"”]$", ""))
                .filter(idea -> !idea.isEmpty() && idea.length() <= MAX_IDEA_CHARS)
                .distinct()
                .limit(MAX_IDEAS)
                .toList();
    }

    /** Openers that fit most questions; the learner finishes the sentence. */
    static List<String> starters(String kaiLine) {
        String line = kaiLine == null ? "" : kaiLine.toLowerCase(Locale.ROOT);
        if (line.contains("why")) {
            return List.of("I think the main reason is…", "Honestly, it's because…");
        }
        if (line.contains("would you") || line.contains("if you could")) {
            return List.of("I think I would…", "That's a tough one, but probably…");
        }
        if (line.contains("what did") || line.contains("how was") || line.contains("last")) {
            return List.of("Well, last time I…", "It was actually pretty… because…");
        }
        return List.of("Well, for me…", "Honestly, I haven't thought about it much, but…");
    }
}
