package com.genzenglish.partner.common;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Thin wrapper over the app's {@link JsonMapper} for jsonb columns and LLM JSON payloads.
 */
@Component
public class Json {

    private final JsonMapper mapper;

    public Json(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        if (value == null) {
            return null;
        }
        return mapper.writeValueAsString(value);
    }

    public <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return mapper.readValue(json, type);
    }

    /** Parses JSON that came from a model; returns null instead of throwing so callers can fall back. */
    public <T> T readLenient(String json, Class<T> type) {
        try {
            return read(stripCodeFence(json), type);
        } catch (JacksonException | IllegalArgumentException e) {
            return null;
        }
    }

    static String stripCodeFence(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.strip();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).strip();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start > 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return text;
    }
}
