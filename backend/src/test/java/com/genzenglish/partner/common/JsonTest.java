package com.genzenglish.partner.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class JsonTest {

    private final Json json = new Json(JsonMapper.builder().build());

    @Test
    void readsJsonWrappedInCodeFencesOrProse() {
        assertThat(json.readLenient("```json\n{\"a\":1}\n```", Map.class)).containsEntry("a", 1);
        assertThat(json.readLenient("Here you go: {\"a\":2} hope that helps", Map.class)).containsEntry("a", 2);
    }

    @Test
    void returnsNullForGarbageInsteadOfThrowing() {
        assertThat(json.readLenient("not json at all", Map.class)).isNull();
        assertThat(json.readLenient(null, Map.class)).isNull();
    }
}
