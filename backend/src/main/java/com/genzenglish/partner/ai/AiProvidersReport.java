package com.genzenglish.partner.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.genzenglish.partner.ai.llm.LlmClient;
import com.genzenglish.partner.ai.pronunciation.PronunciationScorer;
import com.genzenglish.partner.ai.stt.SpeechToText;
import com.genzenglish.partner.ai.tts.TextToSpeech;

/** Logs which AI providers are live, so a misconfigured deploy is obvious at startup. */
@Component
class AiProvidersReport {

    private static final Logger log = LoggerFactory.getLogger(AiProvidersReport.class);

    private final LlmClient llm;
    private final SpeechToText stt;
    private final TextToSpeech tts;
    private final PronunciationScorer pronunciation;

    AiProvidersReport(LlmClient llm, SpeechToText stt, TextToSpeech tts, PronunciationScorer pronunciation) {
        this.llm = llm;
        this.stt = stt;
        this.tts = tts;
        this.pronunciation = pronunciation;
    }

    @EventListener(ApplicationReadyEvent.class)
    void report() {
        log.info("AI providers: llm={}, stt={}, tts={}, pronunciation={}", llm.providerName(), stt.providerName(),
                tts.providerName(), pronunciation.providerName());
    }
}
