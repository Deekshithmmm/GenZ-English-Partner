package com.genzenglish.partner.ai.stt;

/**
 * Turns learner audio into text. Selected by {@code app.ai.stt.provider}. Real providers must be configured for
 * Indian English ({@code en-IN}) so accents aren't "corrected" into different words.
 */
public interface SpeechToText {

    Transcript transcribe(SpeechToTextRequest request);

    String providerName();
}
