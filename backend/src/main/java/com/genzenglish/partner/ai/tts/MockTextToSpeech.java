package com.genzenglish.partner.ai.tts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Asks the app to use on-device speech, so Luna still talks with no TTS keys configured. */
@Component
@ConditionalOnProperty(prefix = "app.ai.tts", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockTextToSpeech implements TextToSpeech {

    @Override
    public SpeechOutput synthesize(TextToSpeechRequest request) {
        return SpeechOutput.device(request.voice(), request.rate());
    }

    @Override
    public String providerName() {
        return "mock";
    }
}
