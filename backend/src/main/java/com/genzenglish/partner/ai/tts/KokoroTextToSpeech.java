package com.genzenglish.partner.ai.tts;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.genzenglish.partner.profile.TutorVoice;

import tools.jackson.databind.json.JsonMapper;

/**
 * Luna's neural voice from the self-hosted Kokoro service ({@code tts/server.py}, {@code app.ai.tts.kokoro-url}). It
 * speaks English only; other languages are left to the phone's voice. Making speech on a CPU can take longer than
 * playing it, so the app asks for it a sentence at a time ({@link #voicesBySentence()}).
 */
@Component
@ConditionalOnProperty(prefix = "app.ai.tts", name = "provider", havingValue = "kokoro")
public class KokoroTextToSpeech implements TextToSpeech {

    private static final Logger log = LoggerFactory.getLogger(KokoroTextToSpeech.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A Kokoro voice and the pace Luna speaks it at, before the learner's own speed setting. */
    private record Voice(String name, double pace) {
    }

    // "Soft" is af_heart, Kokoro's warmest and best-rated voice, a touch calmer than normal; "Bright" is the livelier
    // af_bella.
    private static final Map<TutorVoice, Voice> VOICES = Map.of(
            TutorVoice.KAI_WARM, new Voice("af_heart", 0.95),
            TutorVoice.KAI_BRIGHT, new Voice("af_bella", 1.0));

    private final RestClient http;
    private final String url;

    public KokoroTextToSpeech(@Value("${app.ai.tts.kokoro-url:http://localhost:8090}") String url) {
        this.url = url;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
        // Clips are made one at a time, so a request can wait behind others for a while.
        requests.setReadTimeout(Duration.ofSeconds(90));
        this.http = RestClient.builder().baseUrl(url).requestFactory(requests).build();
    }

    @Override
    public SpeechOutput synthesize(TextToSpeechRequest request) {
        if (!request.languageTag().toLowerCase(Locale.ROOT).startsWith("en")) {
            return SpeechOutput.device(request.voice(), request.rate());
        }
        Voice voice = VOICES.getOrDefault(request.voice(), VOICES.get(TutorVoice.KAI_WARM));
        double speed = Math.clamp(voice.pace() * request.rate(), 0.5, 1.5);
        // Sent as bytes with a length: the service reads plain bodies, and the client would otherwise stream chunks.
        byte[] body = JSON.writeValueAsBytes(Map.of("text", request.text(), "voice", voice.name(), "speed", speed,
                "priority", request.early() ? "later" : "now"));
        byte[] audio = http.post()
                .uri("/speak")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .body(body)
                .retrieve()
                .body(byte[].class);
        if (audio == null || audio.length == 0) {
            throw new IllegalStateException("The voice service sent no audio");
        }
        // The speed is in the audio already, so the app plays it at normal speed.
        return SpeechOutput.audio(audio, "audio/mpeg", request.voice(), 1.0);
    }

    @Override
    public String providerName() {
        return "kokoro";
    }

    @Override
    public boolean voicesBySentence() {
        return true;
    }

    @Override
    public List<String> languages() {
        return List.of("en");
    }

    /** Says at startup whether the voice service answers, so a stopped one is easy to spot. */
    @EventListener(ApplicationReadyEvent.class)
    void checkService() {
        try {
            http.get().uri("/health").retrieve().toBodilessEntity();
            log.info("Luna's voice service is up at {}", url);
        } catch (RestClientException e) {
            log.warn("Luna's voice service at {} isn't answering ({}); Luna uses the phone's voice until it is. "
                    + "Start it with: docker compose up -d tts", url, e.getMessage());
        }
    }
}
