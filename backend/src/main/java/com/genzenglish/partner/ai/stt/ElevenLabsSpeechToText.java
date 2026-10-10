package com.genzenglish.partner.ai.stt;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.genzenglish.partner.ai.AudioClip;

import tools.jackson.databind.JsonNode;

/**
 * Hears the learner with ElevenLabs speech-to-text (Scribe), on the same account as Luna's voice. It writes down what
 * was actually said, slips included ("Yesterday I go..."), and is told the answer is in English so an Indian accent
 * isn't mistaken for another language. The context hint and the drill sentence are deliberately not sent: hinting
 * what the learner should say would "hear" the right answer instead of theirs.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai.stt", name = "provider", havingValue = "elevenlabs")
public class ElevenLabsSpeechToText implements SpeechToText {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsSpeechToText.class);

    /** Pauses before trying again when ElevenLabs says too many requests are running on the account. */
    private static final long[] RETRY_AFTER_MS = {400, 1000, 2000};

    /** File names by recording type; ElevenLabs reads the format from the audio, the name just keeps it tidy. */
    private static final Map<String, String> FILE_NAMES = Map.of(
            "audio/webm", "answer.webm",
            "audio/mp4", "answer.m4a",
            "audio/m4a", "answer.m4a",
            "audio/x-m4a", "answer.m4a",
            "audio/aac", "answer.aac",
            "audio/mpeg", "answer.mp3",
            "audio/wav", "answer.wav",
            "audio/x-wav", "answer.wav",
            "audio/ogg", "answer.ogg");

    private final RestClient http;
    private final String apiKey;
    private final String model;

    public ElevenLabsSpeechToText(@Value("${app.ai.stt.elevenlabs.api-key:}") String apiKey,
            @Value("${app.ai.stt.elevenlabs.model:scribe_v2}") String model,
            @Value("${app.ai.stt.elevenlabs.url:https://api.elevenlabs.io}") String url) {
        this.apiKey = apiKey.strip();
        this.model = model.strip();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(Duration.ofSeconds(45));
        // Recordings are small; buffering sends them with a Content-Length instead of in chunks.
        this.http = RestClient.builder().baseUrl(url).requestFactory(new BufferingClientHttpRequestFactory(requests))
                .build();
    }

    @Override
    public Transcript transcribe(SpeechToTextRequest request) {
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("STT_PROVIDER is elevenlabs but ELEVENLABS_API_KEY is empty");
        }
        AudioClip audio = request.audio();
        MediaType type = mediaType(audio.contentType());
        HttpHeaders file = new HttpHeaders();
        file.setContentType(type);
        file.setContentDispositionFormData("file",
                FILE_NAMES.getOrDefault(type.getType() + "/" + type.getSubtype(), "answer"));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model_id", model);
        form.add("language_code", request.languageTag().split("-")[0].toLowerCase(Locale.ROOT));
        form.add("tag_audio_events", "false");
        // Nothing uses word timings, and leaving them out brings the transcript back about 0.3 s sooner.
        form.add("timestamps_granularity", "none");
        form.add("file", new HttpEntity<>(audio.bytes(), file));
        return transcript(callWithRetries(form));
    }

    /** One transcription, asked for again after a short pause if the account is busy (Luna may be talking). */
    private JsonNode callWithRetries(MultiValueMap<String, Object> form) {
        for (int attempt = 0; ; attempt++) {
            try {
                JsonNode body = http.post()
                        .uri("/v1/speech-to-text")
                        .header("xi-api-key", apiKey)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(form)
                        .retrieve()
                        .body(JsonNode.class);
                if (body == null) {
                    throw new IllegalStateException("ElevenLabs sent no transcript");
                }
                return body;
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt >= RETRY_AFTER_MS.length) {
                    throw e;
                }
                sleep(RETRY_AFTER_MS[attempt]);
            }
        }
    }

    /** The words as said, each with its timing (for pace and pauses) and how sure ElevenLabs was of it. */
    static Transcript transcript(JsonNode body) {
        List<Transcript.Word> words = new ArrayList<>();
        double sure = 0;
        for (JsonNode word : body.path("words")) {
            String text = word.path("text").asString("").strip();
            if (!"word".equals(word.path("type").asString("word")) || text.isEmpty()) {
                continue;
            }
            double confidence = word.has("logprob") ? Math.exp(Math.min(0, word.path("logprob").asDouble(0))) : 1.0;
            words.add(new Transcript.Word(text, Math.round(word.path("start").asDouble(0) * 1000),
                    Math.round(word.path("end").asDouble(0) * 1000), confidence));
            sure += confidence;
        }
        String text = body.path("text").asString("");
        double confidence = words.isEmpty() ? (text.isBlank() ? 0 : 1) : sure / words.size();
        return new Transcript(text, confidence, words);
    }

    private static MediaType mediaType(String contentType) {
        try {
            return contentType == null || contentType.isBlank() ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
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

    /** Says at startup whether learners can be heard, so a missing key is easy to spot. */
    @EventListener(ApplicationReadyEvent.class)
    void report() {
        if (apiKey.isEmpty()) {
            log.warn("STT_PROVIDER is elevenlabs but ELEVENLABS_API_KEY is empty; spoken answers fail until it's set "
                    + "in .env (learners can still type)");
        } else {
            log.info("Luna hears learners with ElevenLabs speech-to-text ({})", model);
        }
    }
}
