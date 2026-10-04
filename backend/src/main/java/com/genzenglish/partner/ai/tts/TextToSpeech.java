package com.genzenglish.partner.ai.tts;

import java.util.List;

/**
 * Luna's voice. Selected by {@code app.ai.tts.provider}.
 */
public interface TextToSpeech {

    SpeechOutput synthesize(TextToSpeechRequest request);

    String providerName();

    /**
     * True when the app should voice Luna's lines itself, a sentence at a time through {@code POST /api/v1/speech},
     * instead of getting one clip per line with the line: a voice that takes longer to make than to play would
     * otherwise hold up every reply. Luna then starts talking as soon as her first sentence is ready.
     */
    default boolean voicesBySentence() {
        return false;
    }

    /** The languages this voice speaks, as primary language subtags ("en", "kn"); "*" for any. */
    default List<String> languages() {
        return List.of();
    }

    /**
     * What identifies this voice's clips, for keeping them on disk so the same sentence is never paid for twice;
     * null when clips shouldn't be kept (a free voice with its own cache, or none).
     */
    default String cacheTag() {
        return null;
    }
}
