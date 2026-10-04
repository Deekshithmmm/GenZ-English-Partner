package com.genzenglish.partner.ai.llm;

import java.util.List;

/**
 * @param jsonOutput ask the provider for a single JSON object (callers still validate and fall back).
 */
public record LlmRequest(
        LlmTask task,
        String system,
        List<LlmMessage> messages,
        int maxTokens,
        double temperature,
        boolean jsonOutput) {

    public LlmRequest {
        messages = List.copyOf(messages);
    }

    public static LlmRequest text(LlmTask task, String system, List<LlmMessage> messages, int maxTokens) {
        return new LlmRequest(task, system, messages, maxTokens, 0.7, false);
    }

    public static LlmRequest json(LlmTask task, String system, List<LlmMessage> messages, int maxTokens) {
        return new LlmRequest(task, system, messages, maxTokens, 0.2, true);
    }

    public String lastUserMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == LlmMessage.Role.USER) {
                return messages.get(i).content();
            }
        }
        return "";
    }
}
