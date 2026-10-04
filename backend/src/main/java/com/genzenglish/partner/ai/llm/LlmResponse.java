package com.genzenglish.partner.ai.llm;

public record LlmResponse(String text, int inputTokens, int outputTokens, long latencyMs) {
}
