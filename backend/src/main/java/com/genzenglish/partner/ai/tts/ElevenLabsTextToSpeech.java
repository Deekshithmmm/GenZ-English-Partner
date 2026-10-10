package com.genzenglish.partner.ai.tts;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
 * sentence is only ever paid for once. Luna's English lines in a conversation use the low-latency live model (Flash),
 * which makes a whole reply in about half a second; lessons, and every language Flash doesn't speak, use the main
 * model.
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
    /** Languages Luna's live lines may use the live model for; Flash doesn't speak Kannada. */
    private static final Set<String> LIVE_LANGUAGES = Set.of("en");

    private final RestClient http;
    /** For streamed lines: read piece by piece as ElevenLabs sends them. */
    private final HttpClient streamer = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final String url;
    private final String apiKey;
    private final String voiceId;
    private final String model;
    /** For whole conversation lines the learner is waiting on; empty to use {@link #model} for everything. */
    private final String liveModel;
    /**
     * ElevenLabs runs only so many requests at once per account (2 on the free plan) and refuses the rest. Every request
     * takes a slot; sentences fetched ahead may only use all but one, so speech that's about to play never waits for them.
     */
    private final Semaphore slots;
    private final Semaphore earlySlots;

    public ElevenLabsTextToSpeech(@Value("${app.ai.tts.elevenlabs.api-key:}") String apiKey,
            @Value("${app.ai.tts.elevenlabs.voice-id:}") String voiceId,
            @Value("${app.ai.tts.elevenlabs.model:eleven_v4_turbo}") String model,
            @Value("${app.ai.tts.elevenlabs.live-model:eleven_flash_v2_5}") String liveModel,
            @Value("${app.ai.tts.elevenlabs.url:https://api.elevenlabs.io}") String url,
            @Value("${app.ai.tts.elevenlabs.max-concurrent:2}") int maxConcurrent) {
        this.apiKey = apiKey.strip();
        this.voiceId = voiceId.strip();
        this.model = model.strip();
        this.liveModel = liveModel.strip();
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
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

    /** What ElevenLabs is asked for: the text, the model (live lines in English get the fast one), language and pace. */
    private byte[] body(TextToSpeechRequest request) {
        double speed = Math.clamp(PACE.getOrDefault(request.voice(), 1.0) * request.rate(), 0.7, 1.2);
        String language = request.languageTag().split("-")[0].toLowerCase(Locale.ROOT);
        boolean live = request.live() && !liveModel.isEmpty() && LIVE_LANGUAGES.contains(language);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", request.text());
        body.put("model_id", live ? liveModel : model);
        body.put("language_code", language);
        body.put("voice_settings", Map.of("stability", 0.5, "similarity_boost", 0.75, "speed", speed));
        return JSON.writeValueAsBytes(body);
    }

    @Override
    public SpeechOutput synthesize(TextToSpeechRequest request) {
        if (!configured()) {
            return SpeechOutput.device(request.voice(), request.rate());
        }
        byte[] json = body(request);
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

    /**
     * Streams a line from ElevenLabs as it's made (the first audio of a live line comes in about a third of a second),
     * handing each piece over as it arrives. Asked again after a short pause if the account is busy.
     */
    @Override
    public void stream(TextToSpeechRequest request, Consumer<byte[]> onAudio) {
        if (!configured()) {
            throw new IllegalStateException("ElevenLabs isn't set up (no key or voice)");
        }
        HttpRequest call = HttpRequest.newBuilder(URI.create(url + "/v1/text-to-speech/" + voiceId
                        + "/stream?output_format=" + FORMAT))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body(request)))
                .build();
        acquire(slots, WAIT_NOW);
        try {
            for (int attempt = 0; ; attempt++) {
                HttpResponse<InputStream> response = streamer.send(call, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = response.body()) {
                    if (response.statusCode() == 429 && attempt < RETRY_AFTER_MS.length) {
                        sleep(RETRY_AFTER_MS[attempt]);
                        continue;
                    }
                    if (response.statusCode() != 200) {
                        String error = new String(in.readNBytes(300), StandardCharsets.UTF_8);
                        throw new IllegalStateException("ElevenLabs answered " + response.statusCode() + ": " + error);
                    }
                    byte[] buffer = new byte[8192];
                    boolean any = false;
                    for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                        if (read > 0) {
                            any = true;
                            onAudio.accept(Arrays.copyOf(buffer, read));
                        }
                    }
                    if (!any) {
                        throw new IllegalStateException("ElevenLabs sent no audio");
                    }
                    return;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't stream from ElevenLabs", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while streaming from ElevenLabs", e);
        } finally {
            slots.release();
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
    public boolean makesLiveLines() {
        return configured() && !liveModel.isEmpty();
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
            log.info("Luna speaks with the ElevenLabs voice \"{}\" ({}; conversation lines: {})", voice == null
                    ? voiceId : voice.path("name").asString(voiceId), model, liveModel.isEmpty() ? model : liveModel);
        } catch (RestClientException e) {
            log.warn("ElevenLabs didn't accept the key or voice ID ({}); Luna uses the phone's voice until it does",
                    e.getMessage());
        }
    }
}
