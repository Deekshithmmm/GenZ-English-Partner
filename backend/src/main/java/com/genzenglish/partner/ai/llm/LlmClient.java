package com.genzenglish.partner.ai.llm;

import java.util.function.Consumer;

/**
 * Chat-model provider for Luna's replies and feedback. Selected by {@code app.ai.llm.provider}.
 * Implementations throw {@link LlmException} on provider failure so callers can fall back gracefully.
 */
public interface LlmClient {

    LlmResponse complete(LlmRequest request);

    /**
     * Streams text as it's generated. {@code onDelta} is called on the calling thread with each new chunk.
     *
     * @return the full response once the stream ends
     */
    LlmResponse stream(LlmRequest request, Consumer<String> onDelta);

    String providerName();
}
