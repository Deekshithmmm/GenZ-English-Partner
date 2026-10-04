package com.genzenglish.partner.ai.tts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.genzenglish.partner.profile.TutorVoice;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Talks to a stand-in for the voice service (tts/server.py) over real HTTP. */
class KokoroTextToSpeechTest {

    private static final byte[] MP3 = {(byte) 0xFF, (byte) 0xF3, 0x44, 0x01};

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private final List<String> lengths = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private int status = 200;
    private KokoroTextToSpeech tts;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/speak", exchange -> {
            // The voice service reads plain bodies: the request must say how long it is, not come in chunks.
            lengths.add(String.valueOf(exchange.getRequestHeaders().getFirst("Content-Length")));
            requests.add(json.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] body = status == 200 ? MP3 : "{\"error\":\"busy\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", status == 200 ? "audio/mpeg" : "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        tts = new KokoroTextToSpeech("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void englishBecomesAClipInKaisSoftVoiceAtHerPace() {
        SpeechOutput out = tts.synthesize(new TextToSpeechRequest("Hi! I'm Luna.", TutorVoice.KAI_WARM, 1.0, "en-IN", false));

        assertThat(out.mode()).isEqualTo(SpeechOutput.Mode.AUDIO);
        assertThat(out.audio()).isEqualTo(MP3);
        assertThat(out.contentType()).isEqualTo("audio/mpeg");
        // The pace is made into the clip, so the app plays it at normal speed.
        assertThat(out.rate()).isEqualTo(1.0);
        JsonNode sent = requests.getFirst();
        assertThat(lengths.getFirst()).matches("\\d+");
        assertThat(sent.get("text").asString()).isEqualTo("Hi! I'm Luna.");
        assertThat(sent.get("voice").asString()).isEqualTo("af_heart");
        assertThat(sent.get("speed").asDouble()).isEqualTo(0.95);
        assertThat(sent.get("priority").asString()).isEqualTo("now");
    }

    @Test
    void theBrightVoiceAndTheLearnersSpeedCarryThroughAndEarlyFetchesWait() {
        tts.synthesize(new TextToSpeechRequest("Ready?", TutorVoice.KAI_BRIGHT, 1.2, "en-US", true));

        JsonNode sent = requests.getFirst();
        assertThat(sent.get("voice").asString()).isEqualTo("af_bella");
        assertThat(sent.get("speed").asDouble()).isEqualTo(1.2);
        assertThat(sent.get("priority").asString()).isEqualTo("later");
    }

    @Test
    void otherLanguagesAreLeftToThePhone() {
        SpeechOutput out = tts.synthesize(new TextToSpeechRequest("ನಮಸ್ತೆ", TutorVoice.KAI_WARM, 1.0, "kn-IN", false));

        assertThat(out.mode()).isEqualTo(SpeechOutput.Mode.DEVICE);
        assertThat(requests).isEmpty();
    }

    @Test
    void aFailingServiceThrowsSoKaiVoiceCanFallBack() {
        status = 503;

        assertThatThrownBy(() -> tts.synthesize(
                new TextToSpeechRequest("Hello.", TutorVoice.KAI_WARM, 1.0, "en-IN", false)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void itVoicesBySentence() {
        assertThat(tts.voicesBySentence()).isTrue();
        assertThat(tts.providerName()).isEqualTo("kokoro");
    }
}
