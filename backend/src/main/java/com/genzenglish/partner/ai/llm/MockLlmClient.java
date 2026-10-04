package com.genzenglish.partner.ai.llm;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, offline LLM. Delegates to a {@link MockLlmResponder} per task and streams word by word so the app's
 * streaming path is exercised locally.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai.llm", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    static final String FALLBACK_REPLY = "Nice. Tell me a bit more about that?";

    private final Map<LlmTask, MockLlmResponder> responders = new EnumMap<>(LlmTask.class);

    public MockLlmClient(List<MockLlmResponder> responders) {
        responders.forEach(r -> this.responders.put(r.task(), r));
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long start = System.nanoTime();
        MockLlmResponder responder = responders.get(request.task());
        String text = responder != null ? responder.respond(request) : FALLBACK_REPLY;
        return new LlmResponse(text, estimateTokens(request), estimateTokens(text), elapsedMs(start));
    }

    @Override
    public LlmResponse stream(LlmRequest request, Consumer<String> onDelta) {
        LlmResponse full = complete(request);
        String[] words = full.text().split("(?<= )");
        for (String word : words) {
            onDelta.accept(word);
        }
        return full;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    private static int estimateTokens(LlmRequest request) {
        int chars = request.system() == null ? 0 : request.system().length();
        for (LlmMessage message : request.messages()) {
            chars += message.content().length();
        }
        return chars / 4;
    }

    private static int estimateTokens(String text) {
        return text.length() / 4;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
