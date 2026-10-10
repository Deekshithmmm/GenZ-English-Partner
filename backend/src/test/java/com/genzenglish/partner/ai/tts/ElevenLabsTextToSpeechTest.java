package com.genzenglish.partner.ai.tts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.genzenglish.partner.profile.TutorVoice;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Talks to a stand-in for the ElevenLabs API over real HTTP. */
class ElevenLabsTextToSpeechTest {

    private static final byte[] MP3 = {(byte) 0xFF, (byte) 0xF3, 0x44, 0x01};

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<HttpExchange> exchanges = new CopyOnWriteArrayList<>();
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    /** Answers for the next requests, before falling back to {@link #status}. */
    private final Queue<Integer> upcoming = new ConcurrentLinkedQueue<>();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger mostAtOnce = new AtomicInteger();
    private HttpServer server;
    private int status = 200;
    private long delayMs;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/v1/text-to-speech/", exchange -> {
            mostAtOnce.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                exchanges.add(exchange);
                bodies.add(json.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                Thread.sleep(delayMs);
                Integer next = upcoming.poll();
                int answer = next != null ? next : status;
                byte[] body = answer == 200 ? MP3 : "{\"detail\":{\"code\":\"concurrent_limit_exceeded\"}}".getBytes();
                exchange.sendResponseHeaders(answer, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ElevenLabsTextToSpeech tts(String key, String voice) {
        return new ElevenLabsTextToSpeech(key, voice, "eleven_v4_turbo", "eleven_flash_v2_5",
                "http://127.0.0.1:" + server.getAddress().getPort(), 2);
    }

    @Test
    void aLiveLineIsStreamedAsItsMade() {
        ElevenLabsTextToSpeech tts = tts("test-key", "voice123");
        java.io.ByteArrayOutputStream heard = new java.io.ByteArrayOutputStream();

        tts.stream(new TextToSpeechRequest("What did you buy?", TutorVoice.KAI_WARM, 1.0, "en-IN", false, true),
                heard::writeBytes);

        assertThat(heard.toByteArray()).isEqualTo(MP3);
        HttpExchange call = exchanges.getFirst();
        assertThat(call.getRequestURI().getPath()).isEqualTo("/v1/text-to-speech/voice123/stream");
        assertThat(call.getRequestURI().getQuery()).isEqualTo("output_format=mp3_44100_64");
        assertThat(call.getRequestHeaders().getFirst("xi-api-key")).isEqualTo("test-key");
        assertThat(bodies.getFirst().get("model_id").asString()).isEqualTo("eleven_flash_v2_5");
    }

    @Test
    void aBusyAccountIsAskedAgainWhenStreaming() {
        upcoming.add(429);
        java.io.ByteArrayOutputStream heard = new java.io.ByteArrayOutputStream();

        tts("test-key", "voice123").stream(
                new TextToSpeechRequest("Hello!", TutorVoice.KAI_WARM, 1.0, "en-IN", false, true), heard::writeBytes);

        assertThat(heard.toByteArray()).isEqualTo(MP3);
        assertThat(exchanges).hasSize(2);
    }

    @Test
    void aStreamThatIsRefusedThrowsSoTheAppSaysTheLineAnotherWay() {
        status = 401;

        assertThatThrownBy(() -> tts("test-key", "voice123").stream(
                new TextToSpeechRequest("Hello!", TutorVoice.KAI_WARM, 1.0, "en-IN", false, true), piece -> { }))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("401");
    }

    @Test
    void lunasEnglishConversationLinesUseTheLiveModel() {
        ElevenLabsTextToSpeech tts = tts("test-key", "voice123");

        tts.synthesize(new TextToSpeechRequest("What did you buy?", TutorVoice.KAI_WARM, 1.0, "en-IN", false, true));
        // Flash doesn't speak Kannada, and lessons aren't live: both stay on the main model.
        tts.synthesize(new TextToSpeechRequest("ನಾನು ಲೂನಾ.", TutorVoice.KAI_WARM, 1.0, "kn-IN", false, true));
        tts.synthesize(new TextToSpeechRequest("Use am with I.", TutorVoice.KAI_WARM, 1.0, "en-IN", false));

        assertThat(bodies).extracting(b -> b.get("model_id").asString())
                .containsExactly("eleven_flash_v2_5", "eleven_v4_turbo", "eleven_v4_turbo");
        assertThat(tts.makesLiveLines()).isTrue();
        assertThat(tts("", "voice123").makesLiveLines()).as("not set up: the phone speaks").isFalse();
    }

    @Test
    void kannadaAndEnglishBothBecomeClipsInTheChosenVoice() {
        ElevenLabsTextToSpeech tts = tts("test-key", "voice123");

        SpeechOutput kannada = tts.synthesize(new TextToSpeechRequest("ನಾನು ಲೂನಾ.", TutorVoice.KAI_WARM, 1.0, "kn-IN", false));
        SpeechOutput english = tts.synthesize(new TextToSpeechRequest("I'm Luna.", TutorVoice.KAI_WARM, 1.2, "en-IN", true));

        assertThat(kannada.mode()).isEqualTo(SpeechOutput.Mode.AUDIO);
        assertThat(kannada.audio()).isEqualTo(MP3);
        assertThat(english.rate()).isEqualTo(1.0);
        HttpExchange first = exchanges.getFirst();
        assertThat(first.getRequestURI().getPath()).isEqualTo("/v1/text-to-speech/voice123");
        assertThat(first.getRequestURI().getQuery()).isEqualTo("output_format=mp3_44100_64");
        assertThat(first.getRequestHeaders().getFirst("xi-api-key")).isEqualTo("test-key");
        assertThat(first.getRequestHeaders().getFirst("Content-Length")).matches("\\d+");
        JsonNode sent = bodies.getFirst();
        assertThat(sent.get("text").asString()).isEqualTo("ನಾನು ಲೂನಾ.");
        assertThat(sent.get("model_id").asString()).isEqualTo("eleven_v4_turbo");
        assertThat(sent.get("language_code").asString()).isEqualTo("kn");
        assertThat(sent.get("voice_settings").get("speed").asDouble()).isEqualTo(0.95);
        // The learner's speed carries through, within what ElevenLabs takes (0.7 to 1.2).
        assertThat(bodies.get(1).get("language_code").asString()).isEqualTo("en");
        assertThat(bodies.get(1).get("voice_settings").get("speed").asDouble()).isEqualTo(1.14);
    }

    @Test
    void neverAsksForMoreThanTheAccountAllowsAtOnce() throws Exception {
        ElevenLabsTextToSpeech tts = tts("test-key", "voice123");
        delayMs = 150;

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<SpeechOutput>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                boolean early = i % 2 == 0;
                String text = "Sentence " + i + ".";
                results.add(pool.submit(() -> tts.synthesize(
                        new TextToSpeechRequest(text, TutorVoice.KAI_WARM, 1.0, "en-IN", early))));
            }
            for (Future<SpeechOutput> result : results) {
                assertThat(result.get().mode()).isEqualTo(SpeechOutput.Mode.AUDIO);
            }
        }
        // The free plan allows two requests at once; a third would be refused.
        assertThat(mostAtOnce.get()).isEqualTo(2);
        assertThat(exchanges).hasSize(6);
    }

    @Test
    void aBusyAnswerIsAskedAgainAfterAMoment() {
        upcoming.add(429);
        upcoming.add(429);

        SpeechOutput out = tts("test-key", "voice123").synthesize(
                new TextToSpeechRequest("Hello.", TutorVoice.KAI_WARM, 1.0, "en-IN", false));

        assertThat(out.mode()).isEqualTo(SpeechOutput.Mode.AUDIO);
        assertThat(exchanges).hasSize(3);
    }

    @Test
    void itSpeaksEveryLanguageAndKeepsClipsPerVoice() {
        ElevenLabsTextToSpeech tts = tts("test-key", "voice123");

        assertThat(tts.languages()).containsExactly("*");
        assertThat(tts.cacheTag()).isEqualTo("elevenlabs/voice123/eleven_v4_turbo");
        assertThat(tts.voicesBySentence()).isTrue();
    }

    @Test
    void withoutAKeyOrVoiceLunaUsesThePhone() {
        ElevenLabsTextToSpeech tts = tts("", "voice123");

        SpeechOutput out = tts.synthesize(new TextToSpeechRequest("Hello.", TutorVoice.KAI_WARM, 1.0, "en-IN", false));

        assertThat(out.mode()).isEqualTo(SpeechOutput.Mode.DEVICE);
        assertThat(tts.languages()).isEmpty();
        assertThat(exchanges).isEmpty();
    }

    @Test
    void anErrorLikeRunningOutOfCreditsThrowsSoKaiVoiceCanFallBack() {
        status = 401;

        assertThatThrownBy(() -> tts("test-key", "voice123").synthesize(
                new TextToSpeechRequest("Hello.", TutorVoice.KAI_WARM, 1.0, "en-IN", false)))
                .isInstanceOf(RuntimeException.class);
    }
}
