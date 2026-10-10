package com.genzenglish.partner.ai.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.genzenglish.partner.ai.AudioClip;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Talks to a stand-in for the ElevenLabs speech-to-text API over real HTTP. */
class ElevenLabsSpeechToTextTest {

    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01, 0x02};

    /** What ElevenLabs answers for "Yesterday I go to the market" (slip included), trimmed. */
    private static final String HEARD = """
            {"language_code":"eng","language_probability":0.98,
             "text":"Yesterday I go to the market.",
             "words":[
              {"text":"Yesterday","start":0.12,"end":0.62,"type":"word","logprob":-0.0008},
              {"text":" ","start":0.62,"end":0.72,"type":"spacing","logprob":0.0},
              {"text":"I","start":0.72,"end":0.88,"type":"word","logprob":-0.47},
              {"text":" ","start":0.88,"end":0.92,"type":"spacing","logprob":0.0},
              {"text":"go","start":0.92,"end":1.08,"type":"word","logprob":-0.000001},
              {"text":"to","start":1.1,"end":1.2,"type":"word","logprob":-0.01},
              {"text":"the","start":1.2,"end":1.3,"type":"word","logprob":-0.01},
              {"text":"market.","start":1.3,"end":1.8,"type":"word","logprob":-0.02}],
             "audio_duration_secs":2.1}""";

    private final List<HttpExchange> exchanges = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final Queue<Integer> upcoming = new ConcurrentLinkedQueue<>();
    private HttpServer server;
    private int status = 200;
    private String answer = HEARD;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/v1/speech-to-text", exchange -> {
            try (exchange) {
                exchanges.add(exchange);
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
                Integer next = upcoming.poll();
                int code = next != null ? next : status;
                byte[] body = (code == 200 ? answer : "{\"detail\":{\"status\":\"busy\"}}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(code, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ElevenLabsSpeechToText stt(String key) {
        return new ElevenLabsSpeechToText(key, "scribe_v2", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static SpeechToTextRequest answer(String contentType) {
        return SpeechToTextRequest.rehearsing(new AudioClip(WEBM, contentType, 2100), "Say it the right way",
                "Yesterday I went to the market.");
    }

    @Test
    void whatTheLearnerReallySaidComesBackWithTimingsAndConfidence() {
        Transcript heard = stt("test-key").transcribe(answer("audio/webm"));

        // The slip stays: the drill's right answer is never sent as a hint.
        assertThat(heard.text()).isEqualTo("Yesterday I go to the market.");
        assertThat(heard.words()).extracting(Transcript.Word::text)
                .containsExactly("Yesterday", "I", "go", "to", "the", "market.");
        assertThat(heard.words().getFirst().startMs()).isEqualTo(120);
        assertThat(heard.words().getFirst().endMs()).isEqualTo(620);
        assertThat(heard.words().get(1).confidence()).isBetween(0.6, 0.65);
        assertThat(heard.confidence()).isBetween(0.9, 1.0);

        HttpExchange call = exchanges.getFirst();
        assertThat(call.getRequestHeaders().getFirst("xi-api-key")).isEqualTo("test-key");
        assertThat(call.getRequestHeaders().getFirst("Content-Type")).startsWith("multipart/form-data");
        assertThat(call.getRequestHeaders().getFirst("Content-Length")).matches("\\d+");
        String sent = bodies.getFirst();
        assertThat(sent).contains("name=\"model_id\"\r\n").contains("\r\n\r\nscribe_v2\r\n");
        assertThat(sent).contains("name=\"language_code\"").contains("\r\n\r\nen\r\n");
        assertThat(sent).contains("name=\"tag_audio_events\"").contains("\r\n\r\nfalse\r\n");
        assertThat(sent).contains("name=\"file\"; filename=\"answer.webm\"").contains("Content-Type: audio/webm");
        assertThat(sent).doesNotContain("Yesterday I went", "Say it the right way");
    }

    @Test
    void aPhoneRecordingIsSentAsM4a() {
        stt("test-key").transcribe(answer("audio/mp4"));

        assertThat(bodies.getFirst()).contains("filename=\"answer.m4a\"").contains("Content-Type: audio/mp4");
    }

    @Test
    void silenceIsABlankTranscript() {
        answer = """
                {"language_code":"eng","language_probability":0.4,"text":"","words":[]}""";

        Transcript heard = stt("test-key").transcribe(answer("audio/webm"));

        assertThat(heard.isBlank()).isTrue();
        assertThat(heard.confidence()).isZero();
    }

    @Test
    void aBusyAccountIsAskedAgainAfterAMoment() {
        upcoming.add(429);
        upcoming.add(429);

        Transcript heard = stt("test-key").transcribe(answer("audio/webm"));

        assertThat(heard.text()).isEqualTo("Yesterday I go to the market.");
        assertThat(exchanges).hasSize(3);
    }

    @Test
    void aRefusedKeyThrowsSoTheLearnerIsAskedToTryAgainOrType() {
        status = 401;

        assertThatThrownBy(() -> stt("test-key").transcribe(answer("audio/webm")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void withoutAKeyNothingIsSent() {
        assertThatThrownBy(() -> stt("").transcribe(answer("audio/webm")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(exchanges).isEmpty();
    }
}
