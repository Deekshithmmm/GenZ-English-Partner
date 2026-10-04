package com.genzenglish.partner.ai.tts;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.genzenglish.partner.profile.TutorVoice;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Luna's voice from ElevenLabs: one voice (picked on elevenlabs.io and set by its ID) for every language, Kannada
 * and English included. Each clip costs credits, so KaiVoice keeps every clip it makes ({@link #cacheTag()}) and a
 * sentence is only ever paid for once.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai.tts", name = "provider", havingValue = "elevenlabs")
public class ElevenLabsTextToSpeech implements TextToSpeech {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsTextToSpeech.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Small, clear MP3: plenty for speech, and quick to load on a phone. */
    private static final String FORMAT = "mp3_44100_64";

    // The same voice for both settings; "Soft" is a touch calmer. ElevenLabs takes speeds from 0.7 to 1.2.
    private static final Map<TutorVoice, Double> PACE = Map.of(TutorVoice.KAI_WARM, 0.95, TutorVoice.KAI_BRIGHT, 1.05);

    /** How long a sentence waits for a free slot before giving up: longer for one fetched ahead of time. */
    private static final Duration WAIT_NOW = Duration.ofSeconds(20);
    private static final Duration WAIT_EARLY = Duration.ofSeconds(60);
    /** Pauses before trying again when ElevenLabs says too many requests are running at once. */
    private static final long[] RETRY_AFTER_MS = {400, 1000};

    private final RestClient http;
    private final String apiKey;
    private final String voiceId;
    private final String model;
    /**
     * ElevenLabs runs only so many requests at once per account (2 on the free plan) and refuses the rest. Every request
     * takes a slot; sentences fetched ahead may only use all but one, so speech that's about to play never waits for them.
     */
    private final Semaphore slots;
    private final Semaphore earlySlots;

    public ElevenLabsTextToSpeech(@Value("${app.ai.tts.elevenlabs.api-key:}") String apiKey,
            @Value("${app.ai.tts.elevenlabs.voice-id:}") String voiceId,
            @Value("${app.ai.tts.elevenlabs.model:eleven_v4_turbo}") String model,
            @Value("${app.ai.tts.elevenlabs.url:https://api.elevenlabs.io}") String url,
            @Value("${app.ai.tts.elevenlabs.max-concurrent:2}") int maxConcurrent) {
        this.apiKey = apiKey.strip();
        this.voiceId = voiceId.strip();
        this.model = model.strip();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder().baseUrl(url).requestFactory(requests).build();
        int concurrent = Math.max(1, maxConcurrent);
        this.slots = new Semaphore(concurrent, true);
        this.earlySlots = new Semaphore(Math.max(1, concurrent - 1), true);
    }

    private boolean configured() {
        return !apiKey.isEmpty() && !voiceId.isEmpty();
    }

    @Override
    public SpeechOutput synthesize(TextToSpeechRequest request) {
        if (!configured()) {
            return SpeechOutput.device(request.voice(), request.rate());
        }
        double speed = Math.clamp(PACE.getOrDefault(request.voice(), 1.0) * request.rate(), 0.7, 1.2);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", request.text());
        body.put("model_id", model);
        body.put("language_code", request.languageTag().split("-")[0].toLowerCase(Locale.ROOT));
        body.put("voice_settings", Map.of("stability", 0.5, "similarity_boost", 0.75, "speed", speed));
        byte[] json = JSON.writeValueAsBytes(body);
        Semaphore early = request.early() ? earlySlots : null;
        Duration wait = request.early() ? WAIT_EARLY : WAIT_NOW;
        acquire(early, wait);
        try {
            acquire(slots, wait);
            try {
                return SpeechOutput.audio(callWithRetries(json), "audio/mpeg", request.voice(), 1.0);
            } finally {
                slots.release();
            }
        } finally {
            if (early != null) {
                early.release();
            }
        }
    }

    /** One clip, asked for again after a short pause if ElevenLabs is busy with other requests on the account. */
    private byte[] callWithRetries(byte[] json) {
        for (int attempt = 0; ; attempt++) {
            try {
                byte[] audio = http.post()
                        .uri(uri -> uri.path("/v1/text-to-speech/{voice}").queryParam("output_format", FORMAT)
                                .build(voiceId))
                        .header("xi-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.parseMediaType("audio/mpeg"))
                        .contentLength(json.length)
                        .body(json)
                        .retrieve()
                        .body(byte[].class);
                if (audio == null || audio.length == 0) {
                    throw new IllegalStateException("ElevenLabs sent no audio");
                }
                return audio;
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt >= RETRY_AFTER_MS.length) {
                    throw e;
                }
                sleep(RETRY_AFTER_MS[attempt]);
            }
        }
    }

    private static void acquire(Semaphore semaphore, Duration wait) {
        if (semaphore == null) {
            return;
        }
        try {
            if (!semaphore.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("ElevenLabs is busy with other sentences");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for ElevenLabs", e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry ElevenLabs", e);
        }
    }

    @Override
    public String providerName() {
        return "elevenlabs";
    }

    @Override
    public boolean voicesBySentence() {
        return true;
    }

    @Override
    public List<String> languages() {
        return configured() ? List.of("*") : List.of();
    }

    @Override
    public String cacheTag() {
        return "elevenlabs/" + voiceId + "/" + model;
    }

    /** Says at startup whether the key and voice work, so a typo is easy to spot. */
    @EventListener(ApplicationReadyEvent.class)
    void checkVoice() {
        if (!configured()) {
            log.warn("TTS_PROVIDER is elevenlabs but ELEVENLABS_API_KEY or ELEVENLABS_VOICE_ID is empty; "
                    + "Luna uses the phone's voice until both are set in .env");
            return;
        }
        try {
            JsonNode voice = http.get().uri("/v1/voices/{voice}", voiceId).header("xi-api-key", apiKey)
                    .retrieve().body(JsonNode.class);
            log.info("Luna speaks with the ElevenLabs voice \"{}\" ({})", voice == null ? voiceId
                    : voice.path("name").asString(voiceId), model);
        } catch (RestClientException e) {
            log.warn("ElevenLabs didn't accept the key or voice ID ({}); Luna uses the phone's voice until it does",
                    e.getMessage());
        }
    }
}
