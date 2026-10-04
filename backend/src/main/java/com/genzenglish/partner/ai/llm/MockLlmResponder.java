package com.genzenglish.partner.ai.llm;

/**
 * Canned behaviour for one {@link LlmTask} when the mock LLM is active. Features contribute their own responders
 * so the mock can produce realistic, deterministic output without knowing about every feature.
 */
public interface MockLlmResponder {

    LlmTask task();

    String respond(LlmRequest request);
}
