package com.genzenglish.partner.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Talks to a stand-in for the Gemini API over real HTTP. */
class GeminiLlmClientTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Live replies: the strongest model first, then a fast Flash-Lite one. */
    private static final String STRONG = "strong";
    private static final String LITE = "quick-lite";
    /** Summaries and assessments have their own list. */
    private static final String STRONG_SUMMARY = "strong-summary";
    private static final String LITE_SUMMARY = "quick-lite-summary";
    /** How long any model but the last may take to start answering, in these tests. */
    private static final Duration START_WITHIN = Duration.ofMillis(400);

    private record Call(String model, String method, String apiKey, JsonNode body) {
    }

    /**
     * One scripted answer: a status, then its body in pieces (SSE events, or one JSON error). The headers go out after
     * {@code delayMs}; each later piece after another {@code pauseMs}.
     */
    private record Answer(int status, List<String> pieces, long delayMs, long pauseMs) {

        Answer late(long ms) {
            return new Answer(status, pieces, ms, pauseMs);
        }

        Answer pausing(long ms) {
            return new Answer(status, pieces, delayMs, ms);
        }
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Map<String, Queue<Answer>> script = new ConcurrentHashMap<>();
    private Instant now = Instant.parse("2026-10-06T10:00:00Z");
    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/v1beta/models/", exchange -> {
            try (exchange) {
                String path = exchange.getRequestURI().getPath().substring("/v1beta/models/".length());
                String model = path.contains(":") ? path.substring(0, path.indexOf(':')) : path;
                String method = path.contains(":") ? path.substring(path.indexOf(':') + 1) : "get";
                String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                calls.add(new Call(model, method, exchange.getRequestHeaders().getFirst("x-goog-api-key"),
                        raw.isEmpty() ? null : JSON.readTree(raw)));
                Answer answer = script.getOrDefault(model, new ConcurrentLinkedQueue<>()).poll();
                send(exchange, answer != null ? answer : error(500, "nothing scripted"));
            } catch (IOException e) {
                // The client gave up on this answer; that's what some tests are about.
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    /** Closes the connection without a word, as when Google resets it. */
    private static final Answer DROPPED = new Answer(0, List.of(), 0, 0);

    private static void send(HttpExchange exchange, Answer answer) throws IOException {
        if (answer == DROPPED) {
            return;
        }
        pause(answer.delayMs());
        exchange.getResponseHeaders().add("Content-Type",
                answer.status() == 200 ? "text/event-stream" : "application/json");
        exchange.sendResponseHeaders(answer.status(), 0);
        OutputStream body = exchange.getResponseBody();
        for (int i = 0; i < answer.pieces().size(); i++) {
            if (i > 0) {
                pause(answer.pauseMs());
            }
            body.write(answer.pieces().get(i).getBytes(StandardCharsets.UTF_8));
            body.flush();
        }
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void then(String model, Answer... answers) {
        script.computeIfAbsent(model, m -> new ConcurrentLinkedQueue<>()).addAll(List.of(answers));
    }

    /** One streamed event: some text, and on the last one how the answer ended and what it used. */
    private static String event(String text, String ending, boolean usage, boolean thought) {
        String parts = text == null ? "[]" : thought
                ? "[{\"text\":\"Plan: ask about it.\",\"thought\":true},{\"text\":" + JSON.writeValueAsString(text)
                        + "}]"
                : "[{\"text\":" + JSON.writeValueAsString(text) + "}]";
        return "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":" + parts + "}"
                + (ending == null ? "" : ",\"finishReason\":\"" + ending + "\"") + "}]"
                + (usage ? ",\"usageMetadata\":{\"promptTokenCount\":75,\"candidatesTokenCount\":36}" : "")
                + "}\r\n\r\n";
    }

    private static Answer says(String text) {
        return events(text);
    }

    private static Answer events(String... pieces) {
        List<String> events = new ArrayList<>();
        for (int i = 0; i < pieces.length; i++) {
            boolean last = i == pieces.length - 1;
            events.add(event(pieces[i], last ? "STOP" : null, last, false));
        }
        return new Answer(200, events, 0, 0);
    }

    private static Answer ends(String ending) {
        return new Answer(200, List.of(event(null, ending, false, false)), 0, 0);
    }

    /** What Gemini says when a model's free requests are used up, with how long until they come back. */
    private static Answer outOfQuota(String retryDelay) {
        return new Answer(429, List.of("""
                {"error":{"code":429,"message":"You exceeded your current quota.","status":"RESOURCE_EXHAUSTED",
                 "details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":
                 "GenerateRequestsPerDayPerProjectPerModel-FreeTier","quotaValue":"20"}]},
                 {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"%s"}]}}""".formatted(retryDelay)),
                0, 0);
    }

    private static Answer error(int status, String message) {
        return new Answer(status, List.of("{\"error\":{\"code\":%d,\"message\":\"%s\",\"status\":\"X\"}}"
                .formatted(status, message)), 0, 0);
    }

    private GeminiLlmClient gemini(String key) {
        Clock clock = new Clock() {
            @Override
            public Instant instant() {
                return now;
            }

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }
        };
        return new GeminiLlmClient(key, STRONG + "," + LITE, STRONG_SUMMARY + "," + LITE_SUMMARY,
                "http://127.0.0.1:" + server.getAddress().getPort(), START_WITHIN, START_WITHIN,
                Duration.ofSeconds(5), clock);
    }

    private static LlmRequest reply() {
        return LlmRequest.text(LlmTask.TUTOR_REPLY, "You are Luna.",
                List.of(LlmMessage.user("I like cricket."), LlmMessage.assistant("Who's your favourite player?"),
                        LlmMessage.user("Yesterday I go to the market with my friend.")),
                180);
    }

    private static LlmRequest summary() {
        return LlmRequest.json(LlmTask.SESSION_SUMMARY, "Reply with one JSON object.",
                List.of(LlmMessage.user("{\"answer\":\"I go to market\"}")), 700);
    }

    private static String thinking(Call call) {
        return call.body().path("generationConfig").path("thinkingConfig").path("thinkingLevel").asString("");
    }

    @Test
    void aLiveReplyGoesToTheStrongestModelWithTheWholeConversation() {
        then(STRONG, says("Nice! What did you buy there?"));

        LlmResponse response = gemini("test-key").complete(reply());

        assertThat(response.text()).isEqualTo("Nice! What did you buy there?");
        assertThat(response.inputTokens()).isEqualTo(75);
        assertThat(response.outputTokens()).isEqualTo(36);
        Call call = calls.getFirst();
        assertThat(call.model()).isEqualTo(STRONG);
        assertThat(call.method()).isEqualTo("streamGenerateContent");
        assertThat(call.apiKey()).isEqualTo("test-key");
        JsonNode body = call.body();
        assertThat(body.path("systemInstruction").path("parts").path(0).path("text").asString())
                .isEqualTo("You are Luna.");
        assertThat(body.path("contents")).hasSize(3);
        assertThat(body.path("contents").path(1).path("role").asString()).isEqualTo("model");
        assertThat(body.path("contents").path(2).path("role").asString()).isEqualTo("user");
        assertThat(body.path("contents").path(2).path("parts").path(0).path("text").asString())
                .isEqualTo("Yesterday I go to the market with my friend.");
        JsonNode config = body.path("generationConfig");
        // Thinking counts against the limit, so it gets room on top of the reply's own.
        assertThat(config.path("maxOutputTokens").asInt()).isEqualTo(180 + GeminiLlmClient.THINKING_ROOM);
        assertThat(config.path("temperature").asDouble()).isEqualTo(0.7);
        assertThat(thinking(call)).isEqualTo("low");
        assertThat(config.has("responseMimeType")).isFalse();
    }

    @Test
    void summariesHaveTheirOwnModelsAndComeBackAsJson() {
        then(STRONG_SUMMARY, events("{\"corrections\":", "[]}"));

        LlmResponse response = gemini("test-key").complete(summary());

        assertThat(response.text()).isEqualTo("{\"corrections\":[]}");
        assertThat(calls.getFirst().model()).isEqualTo(STRONG_SUMMARY);
        assertThat(calls.getFirst().body().path("generationConfig").path("responseMimeType").asString())
                .isEqualTo("application/json");
    }

    @Test
    void notesOnAnAnswerGoToTheStrongestModels() {
        then(STRONG_SUMMARY, says("{\"corrections\":[]}"));

        gemini("test-key").complete(LlmRequest.json(LlmTask.TURN_FEEDBACK, "Reply with one JSON object.",
                List.of(LlmMessage.user("{\"answer\":\"I am having two sisters\"}")), 700));

        assertThat(calls).extracting(Call::model).containsExactly(STRONG_SUMMARY);
    }

    @Test
    void aModelOutOfQuotaIsLeftAloneForExactlyAsLongAsGoogleSays() {
        GeminiLlmClient gemini = gemini("test-key");
        then(STRONG, outOfQuota("120s"));
        then(LITE, says("One."), says("Two."));

        assertThat(gemini.complete(reply()).text()).isEqualTo("One.");
        now = now.plusSeconds(100);
        assertThat(gemini.complete(reply()).text()).isEqualTo("Two.");
        assertThat(calls).extracting(Call::model).containsExactly(STRONG, LITE, LITE);

        // Back when the quota comes back, not ten minutes later.
        now = now.plusSeconds(25);
        then(STRONG, says("Fresh quota."));
        assertThat(gemini.complete(reply()).text()).isEqualTo("Fresh quota.");
    }

    @Test
    void googlesRetryDelayIsRead() {
        assertThat(GeminiLlmClient.retryDelay(outOfQuota("37047s").pieces().getFirst()))
                .isEqualTo(Duration.ofSeconds(37048));
        assertThat(GeminiLlmClient.retryDelay(outOfQuota("41.5s").pieces().getFirst()))
                .isEqualTo(Duration.ofMillis(42500));
        assertThat(GeminiLlmClient.retryDelay(outOfQuota("200000s").pieces().getFirst()))
                .isEqualTo(GeminiLlmClient.LONGEST_QUOTA_WAIT);
        assertThat(GeminiLlmClient.retryDelay("{\"error\":{\"code\":429,\"message\":\"slow down\"}}")).isNull();
        assertThat(GeminiLlmClient.retryDelay("not json")).isNull();
    }

    @Test
    void aBusyModelHandsOverToTheNextWhichThinksLess() {
        then(STRONG, error(503, "This model is currently experiencing high demand."));
        then(LITE, says("Tell me more!"));

        assertThat(gemini("test-key").complete(reply()).text()).isEqualTo("Tell me more!");
        assertThat(calls).extracting(Call::model).containsExactly(STRONG, LITE);
        assertThat(thinking(calls.get(1))).isEqualTo("minimal");
    }

    @Test
    void aModelSlowToStartAnsweringHandsOverToTheNext() {
        then(STRONG, says("Too late.").late(START_WITHIN.toMillis() * 4));
        then(LITE, says("On time!"));

        long started = System.nanoTime();
        assertThat(gemini("test-key").complete(reply()).text()).isEqualTo("On time!");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(START_WITHIN.multipliedBy(4));
    }

    @Test
    void aModelThatStartedInTimeMayTakeLongerToFinish() {
        // The first words come at once; the rest takes longer than the start limit, which must not cut it off.
        then(STRONG, events("That sounds ", "like a great ", "day out!").pausing(START_WITHIN.toMillis() * 2));
        List<String> pieces = new ArrayList<>();

        LlmResponse response = gemini("test-key").stream(reply(), pieces::add);

        assertThat(response.text()).isEqualTo("That sounds like a great day out!");
        assertThat(pieces).containsExactly("That sounds ", "like a great ", "day out!");
        assertThat(calls).extracting(Call::model).containsExactly(STRONG);
    }

    @Test
    void aDroppedConnectionIsAskedAgainOfTheSameModelWithoutBenchingIt() {
        GeminiLlmClient gemini = gemini("test-key");
        then(STRONG, DROPPED, says("Still here!"), says("And again."));

        assertThat(gemini.complete(reply()).text()).isEqualTo("Still here!");
        // The model wasn't to blame, so the next reply goes to it again.
        assertThat(gemini.complete(reply()).text()).isEqualTo("And again.");
        assertThat(calls).extracting(Call::model).containsExactly(STRONG, STRONG, STRONG);
    }

    @Test
    void aConnectionThatDropsTwiceMovesToTheNextModel() {
        then(STRONG, DROPPED, DROPPED);
        then(LITE, says("Here."));

        assertThat(gemini("test-key").complete(reply()).text()).isEqualTo("Here.");
        assertThat(calls).extracting(Call::model).containsExactly(STRONG, STRONG, LITE);
    }

    @Test
    void theLastModelGetsMoreTimeAsNoOneComesAfterIt() {
        then(STRONG, error(503, "busy"));
        then(LITE, says("Worth the wait.").late(START_WITHIN.toMillis() * 2));

        assertThat(gemini("test-key").complete(reply()).text()).isEqualTo("Worth the wait.");
    }

    @Test
    void aTiredModelRestsLongerEachTimeItFailsAgain() {
        GeminiLlmClient gemini = gemini("test-key");
        then(STRONG, error(503, "busy"));
        then(LITE, says("One."), says("Two."), says("Three."), says("Four."));

        assertThat(gemini.complete(reply()).text()).isEqualTo("One.");
        // Resting: the next reply goes straight to the next model.
        assertThat(gemini.complete(reply()).text()).isEqualTo("Two.");
        assertThat(calls).extracting(Call::model).containsExactly(STRONG, LITE, LITE);

        // Awake again, and busy again: this time it rests twice as long.
        now = now.plus(GeminiLlmClient.REST_WHEN_BUSY).plusSeconds(1);
        then(STRONG, error(503, "busy"));
        assertThat(gemini.complete(reply()).text()).isEqualTo("Three.");
        now = now.plus(GeminiLlmClient.REST_WHEN_BUSY).plusSeconds(1);
        assertThat(gemini.complete(reply()).text()).isEqualTo("Four.");
        assertThat(calls).extracting(Call::model).containsExactly(STRONG, LITE, LITE, STRONG, LITE, LITE);

        // Once it answers, its slate is clean.
        now = now.plus(GeminiLlmClient.REST_WHEN_BUSY).plusSeconds(1);
        then(STRONG, says("Back."));
        assertThat(gemini.complete(reply()).text()).isEqualTo("Back.");
    }

    @Test
    void aUsedUpFreeLimitMovesToAModelWithItsOwnLimit() {
        then(STRONG_SUMMARY, error(429, "Quota exceeded for metric generate_content_free_tier_requests"));
        then(LITE_SUMMARY, says("{\"corrections\":[]}"));

        assertThat(gemini("test-key").complete(summary()).text()).isEqualTo("{\"corrections\":[]}");
    }

    @Test
    void aModelThatDoesNotTakeTheThinkingSettingIsAskedWithoutIt() {
        then(STRONG, error(400, "Thinking level low is not supported for this model."), says("Sure!"));

        assertThat(gemini("test-key").complete(reply()).text()).isEqualTo("Sure!");
        assertThat(calls).hasSize(2);
        assertThat(calls.get(1).body().path("generationConfig").has("thinkingConfig")).isFalse();
    }

    @Test
    void aReplyStreamsPieceByPiece() {
        then(STRONG, events("Nice! ", "What did you ", "buy?"));
        List<String> pieces = new ArrayList<>();

        LlmResponse response = gemini("test-key").stream(reply(), pieces::add);

        assertThat(pieces).containsExactly("Nice! ", "What did you ", "buy?");
        assertThat(response.text()).isEqualTo("Nice! What did you buy?");
        assertThat(response.inputTokens()).isEqualTo(75);
    }

    @Test
    void thoughtsThatComeBackAreNeverShown() {
        then(STRONG, new Answer(200, List.of(event("What did you buy?", "STOP", true, true)), 0, 0));

        assertThat(gemini("test-key").complete(reply()).text()).isEqualTo("What did you buy?");
    }

    @Test
    void aStreamThatFailsBeforeAWordMovesToTheNextModel() {
        then(STRONG, error(503, "busy"));
        then(LITE, events("Hello ", "there!"));
        List<String> pieces = new ArrayList<>();

        assertThat(gemini("test-key").stream(reply(), pieces::add).text()).isEqualTo("Hello there!");
        assertThat(pieces).containsExactly("Hello ", "there!");
    }

    @Test
    void anEmptyAnswerIsAskedOfTheNextModel() {
        then(STRONG, ends("MAX_TOKENS"));
        then(LITE, says("Here you go."));

        assertThat(gemini("test-key").complete(reply()).text()).isEqualTo("Here you go.");
    }

    @Test
    void aWithheldAnswerIsNotAskedAgainSoTheCallerFallsBack() {
        then(STRONG, ends("SAFETY"));

        assertThatThrownBy(() -> gemini("test-key").complete(reply())).isInstanceOf(LlmException.class)
                .hasMessageContaining("SAFETY");
        assertThat(calls).hasSize(1);
    }

    @Test
    void whenNoModelAnswersTheCallerFallsBack() {
        then(STRONG, error(503, "busy"));
        then(LITE, error(429, "quota"));

        assertThatThrownBy(() -> gemini("test-key").complete(reply())).isInstanceOf(LlmException.class)
                .hasMessageContaining("No Gemini model answered");
    }

    @Test
    void aRefusedKeyStopsAtOnce() {
        then(STRONG, error(403, "API key not valid."));

        assertThatThrownBy(() -> gemini("test-key").complete(reply())).isInstanceOf(LlmException.class)
                .hasMessageContaining("403");
        assertThat(calls).hasSize(1);
    }

    @Test
    void withoutAKeyNothingIsSent() {
        assertThatThrownBy(() -> gemini("").complete(reply())).isInstanceOf(LlmException.class);
        assertThat(calls).isEmpty();
    }

    @Test
    void flashLiteModelsThinkLeast() {
        assertThat(GeminiLlmClient.thinkingLevel("gemini-3.5-flash-lite")).isEqualTo("minimal");
        assertThat(GeminiLlmClient.thinkingLevel("gemini-3.8-flash")).isEqualTo("low");
    }
}
