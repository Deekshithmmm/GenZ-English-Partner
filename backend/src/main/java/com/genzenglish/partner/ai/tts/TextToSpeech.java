package com.genzenglish.partner.ai.tts;

import java.util.List;
import java.util.function.Consumer;

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

    /**
     * True when this voice has a model quick enough (under about half a second) to make a whole conversation line
     * while the learner waits ({@link TextToSpeechRequest#live()}). A voice that otherwise goes by sentence then sends
     * Luna's reply with its audio, ready to play, instead of the app asking for each sentence.
     */
    default boolean makesLiveLines() {
        return false;
    }

    /**
     * Makes speech and hands over its audio as it's made, so playback can start before the end is ready. Without a
     * way to stream, the whole clip arrives at once. Throws, like {@link #synthesize}, when there's no audio.
     */
    default void stream(TextToSpeechRequest request, Consumer<byte[]> onAudio) {
        SpeechOutput output = synthesize(request);
        if (output.mode() != SpeechOutput.Mode.AUDIO) {
            throw new IllegalStateException(providerName() + " has no audio for this line");
        }
        onAudio.accept(output.audio());
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
