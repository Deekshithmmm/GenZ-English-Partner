package com.genzenglish.partner.ai.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Luna's replies and feedback from Google's Gemini models. Luna's side of a conversation goes to fast models
 * ({@code reply-models}); corrections, scores and plans go strongest first ({@code smart-models}), since the strongest
 * model's quota is scarce (20 requests a day on the free tier) and is best spent where judgement matters. When a model
 * is busy, slow to start or out of requests, the next one in the list answers and the tired one rests: for as long as
 * Google says when it's out of quota, otherwise longer each time it fails again. Only when none answer does the
 * caller fall back to its built-in reply.
 *
 * <p>Every answer is streamed, so "slow" means slow to start: a model gets a few seconds to say its first words and
 * then all the time it needs to finish, instead of being cut off mid-sentence.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai.llm", name = "provider", havingValue = "gemini")
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Thinking counts against the output limit, so it gets this much room on top of what the caller asked for. */
    static final int THINKING_ROOM = 512;
    /** How long a model is first skipped after it was busy or too slow, ran out of requests, or isn't available. */
    static final Duration REST_WHEN_BUSY = Duration.ofMinutes(5);
    static final Duration REST_WHEN_LIMITED = Duration.ofMinutes(10);
    static final Duration REST_WHEN_MISSING = Duration.ofHours(1);
    /** Each failure in a row doubles the rest, up to this. */
    static final Duration LONGEST_REST = Duration.ofHours(1);
    /** A quota that resets tomorrow is waited out, but never longer than this. */
    static final Duration LONGEST_QUOTA_WAIT = Duration.ofHours(24);
    /** Once it has started, an answer that still hasn't finished after this long has stalled. */
    static final Duration FINISH_WITHIN = Duration.ofSeconds(60);
    /** A dropped connection says nothing about the model, so it's asked again after this pause. */
    static final Duration RETRY_AFTER_DROPPED_CONNECTION = Duration.ofMillis(300);
    /** Answers that mean "not from this model, not now" rather than "never". */
    private static final Set<Integer> BUSY = Set.of(408, 429, 500, 502, 503, 504);
    /** Endings that mean Google withheld the answer; another model wouldn't say it either. */
    private static final Set<String> WITHHELD = Set.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT",
            "SPII", "IMAGE_SAFETY");
    /** Closes answers that stall halfway, so a request never hangs on them. */
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "gemini-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private final String apiKey;
    private final URI baseUrl;
    /** Luna's side of the conversation: fast models, quick to start. */
    private final Tier replies;
    /** Notes on an answer: the strongest models, but the learner is waiting for them too. */
    private final Tier notes;
    /** Summaries, assessments and plans: the strongest models, with more time to start. */
    private final Tier thorough;
    private final Duration lastStartWithin;
    private final Clock clock;
    /**
     * HTTP/1.1, so each answer has a connection of its own: over HTTP/2 every request shares one, and when Google resets
     * it, every model fails at the same moment.
     */
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, Rest> resting = new ConcurrentHashMap<>();

    /**
     * The models for one kind of task, in the order they're tried. Every model but the last must start answering
     * within {@code startWithin}; the last one, with no one after it, gets the client's {@code lastStartWithin}.
     */
    private record Tier(List<String> models, Duration startWithin) {
    }

    /** A model that failed {@code strikes} times in a row, skipped until {@code until}. */
    private record Rest(Instant until, int strikes) {
    }

    /**
     * @param apiKey      GEMINI_API_KEY; when it's empty, {@code googleKey} (GOOGLE_API_KEY, the name Google's own
     *                    tools read) is used instead
     * @param replyModels for Luna's side of the conversation, comma-separated, fastest first
     * @param smartModels for corrections, summaries, assessments and plans, strongest first
     * @param liveStart   how long a model may take to start on something the learner is waiting for
     */
    @Autowired
    GeminiLlmClient(@Value("${app.ai.llm.gemini.api-key:}") String apiKey, @Value("${GOOGLE_API_KEY:}") String googleKey,
            @Value("${app.ai.llm.gemini.reply-models:gemini-3.5-flash-lite,gemini-3.1-flash-lite,gemini-3.8-flash}")
            String replyModels,
            @Value("${app.ai.llm.gemini.smart-models:gemini-3.8-flash,gemini-3.5-flash-lite,gemini-3.1-flash-lite}")
            String smartModels,
            @Value("${app.ai.llm.gemini.url:https://generativelanguage.googleapis.com}") String url,
            @Value("${app.ai.llm.gemini.live-start-within:5s}") Duration liveStart,
            @Value("${app.ai.llm.gemini.smart-start-within:10s}") Duration smartStart,
            @Value("${app.ai.llm.gemini.last-start-within:30s}") Duration lastStart,
            Clock clock) {
        this(apiKey.isBlank() ? googleKey : apiKey, replyModels, smartModels, url, liveStart, smartStart, lastStart,
                clock);
    }

    GeminiLlmClient(String apiKey, String replyModels, String smartModels, String url, Duration liveStart,
            Duration smartStart, Duration lastStart, Clock clock) {
        this.apiKey = apiKey.strip();
        this.baseUrl = URI.create(url.endsWith("/") ? url : url + "/");
        this.replies = new Tier(models(replyModels), liveStart);
        this.notes = new Tier(models(smartModels), liveStart);
        this.thorough = new Tier(models(smartModels), smartStart);
        this.lastStartWithin = lastStart;
        this.clock = clock;
    }

    private Tier tierFor(LlmTask task) {
        if (!task.needsJudgement()) {
            return replies;
        }
        return task.latencySensitive() ? notes : thorough;
    }

    /** Flash-Lite models think least at "minimal"; the bigger ones take "low" as their lightest setting. */
    static String thinkingLevel(String model) {
        return model.contains("lite") ? "minimal" : "low";
    }

    private static List<String> models(String list) {
        List<String> models = Arrays.stream(list.split(",")).map(String::strip).filter(m -> !m.isEmpty()).toList();
        if (models.isEmpty()) {
            throw new IllegalArgumentException("List at least one Gemini model");
        }
        return models;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        return answer(request, null);
    }

    @Override
    public LlmResponse stream(LlmRequest request, Consumer<String> onDelta) {
        return answer(request, onDelta);
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    /** Asks each model of the request's tier in turn until one answers. */
    private LlmResponse answer(LlmRequest request, Consumer<String> onDelta) {
        if (apiKey.isEmpty()) {
            throw new LlmException("LLM_PROVIDER is gemini but neither GEMINI_API_KEY nor GOOGLE_API_KEY is set");
        }
        Tier tier = tierFor(request.task());
        List<String> models = awake(tier.models());
        Unavailable last = null;
        for (int i = 0; i < models.size(); i++) {
            String model = models.get(i);
            Duration startWithin = i + 1 < models.size() ? tier.startWithin() : lastStartWithin;
            Said said = new Said(onDelta);
            try {
                LlmResponse response = askModel(model, request, startWithin, said);
                resting.remove(model);
                if (i > 0) {
                    log.info("Gemini {} answered in {} ms", model, response.latencyMs());
                }
                return response;
            } catch (Unavailable e) {
                last = e;
            }
            if (last.waitExactly() != null) {
                // Out of quota: Google says when it's back, so ask again then and not before.
                resting.put(model, new Rest(clock.instant().plus(last.waitExactly()), 1));
            } else if (!last.rest().isZero()) {
                rest(model, last.rest());
            }
            if (said.any()) {
                // Part of the line has already been shown; another model would start a different one.
                throw new LlmException("Gemini " + model + " stopped mid-reply: " + last.getMessage(), last);
            }
            if (i + 1 < models.size()) {
                log.warn("Gemini {} didn't answer ({}); asking {}", model, last.getMessage(), models.get(i + 1));
            }
        }
        throw new LlmException("No Gemini model answered (" + (last == null ? "none listed" : last.getMessage())
                + ")", last);
    }

    /** The tier's models in order, skipping those that are resting, unless all of them are. */
    private List<String> awake(List<String> models) {
        Instant now = clock.instant();
        List<String> awake = models.stream()
                .filter(m -> {
                    Rest rest = resting.get(m);
                    return rest == null || !rest.until().isAfter(now);
                })
                .toList();
        return awake.isEmpty() ? models : awake;
    }

    /** Skips {@code model} for {@code base}, doubled for every failure in a row before this one. */
    private void rest(String model, Duration base) {
        resting.compute(model, (m, previous) -> {
            int strikes = previous == null ? 1 : previous.strikes() + 1;
            Duration rest = base.multipliedBy(1L << Math.min(strikes - 1, 10));
            return new Rest(clock.instant().plus(rest.compareTo(LONGEST_REST) > 0 ? LONGEST_REST : rest), strikes);
        });
    }

    /**
     * One model's answer. It's asked again without the thinking setting if it refuses that, and once more if the
     * connection dropped before it said anything.
     */
    private LlmResponse askModel(String model, LlmRequest request, Duration startWithin, Said said) {
        String thinking = thinkingLevel(model);
        boolean reconnected = false;
        while (true) {
            try {
                return ask(model, request, thinking, startWithin, said);
            } catch (ThinkingRefused e) {
                thinking = null;
            } catch (Unavailable e) {
                if (!e.connectionLost() || reconnected || said.any()) {
                    throw e;
                }
                reconnected = true;
                log.info("Gemini {}: {}; asking again", model, e.getMessage());
                pause(RETRY_AFTER_DROPPED_CONNECTION);
            }
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting for Gemini", e);
        }
    }

    /** One model's streamed answer: it must start within {@code startWithin}, then may take what it needs. */
    private LlmResponse ask(String model, LlmRequest request, String thinkingLevel, Duration startWithin, Said said) {
        HttpRequest call = HttpRequest.newBuilder(baseUrl.resolve("v1beta/models/" + model
                        + ":streamGenerateContent?alt=sse"))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body(request, thinkingLevel))))
                .build();
        long started = System.nanoTime();
        CompletableFuture<HttpResponse<InputStream>> pending = http.sendAsync(call,
                HttpResponse.BodyHandlers.ofInputStream());
        HttpResponse<InputStream> response;
        try {
            // Gemini sends the headers with its first words, so this is how long it may take to start answering.
            response = pending.get(startWithin.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new Unavailable("didn't start answering within " + startWithin.toMillis() + " ms", REST_WHEN_BUSY,
                    null);
        } catch (ExecutionException e) {
            throw Unavailable.connectionLost("couldn't reach it: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting for Gemini", e);
        }
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                throw failure(response.statusCode(), new String(in.readAllBytes(), StandardCharsets.UTF_8),
                        thinkingLevel != null);
            }
            ScheduledFuture<?> giveUp = WATCHDOG.schedule(() -> closeQuietly(in), FINISH_WITHIN.toMillis(),
                    TimeUnit.MILLISECONDS);
            try {
                return streamed(in, said, started);
            } finally {
                giveUp.cancel(false);
            }
        } catch (IOException e) {
            throw Unavailable.connectionLost("the answer broke off: " + e.getMessage(), e);
        } catch (JacksonException e) {
            throw new Unavailable("unreadable answer: " + e.getOriginalMessage(), REST_WHEN_BUSY, e);
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException e) {
            // Closing is only to stop waiting; there's nothing else to do.
        }
    }

    static Map<String, Object> body(LlmRequest request, String thinkingLevel) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (request.system() != null && !request.system().isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.system()))));
        }
        List<Map<String, Object>> contents = new ArrayList<>();
        for (LlmMessage message : request.messages()) {
            contents.add(Map.of("role", message.role() == LlmMessage.Role.ASSISTANT ? "model" : "user",
                    "parts", List.of(Map.of("text", message.content()))));
        }
        body.put("contents", contents);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("maxOutputTokens", request.maxTokens() + THINKING_ROOM);
        config.put("temperature", request.temperature());
        if (request.jsonOutput()) {
            config.put("responseMimeType", "application/json");
        }
        if (thinkingLevel != null) {
            config.put("thinkingConfig", Map.of("thinkingLevel", thinkingLevel));
        }
        body.put("generationConfig", config);
        return body;
    }

    /** Reads a streamed answer event by event, passing each new piece of text on as it arrives. */
    private static LlmResponse streamed(InputStream in, Said said, long started) throws IOException {
        StringBuilder text = new StringBuilder();
        JsonNode last = null;
        BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        for (String line = lines.readLine(); line != null; line = lines.readLine()) {
            if (!line.startsWith("data:")) {
                continue;
            }
            JsonNode event = JSON.readTree(line.substring("data:".length()).strip());
            if (event.has("error")) {
                throw new Unavailable("error while answering: " + event.path("error").path("message")
                        .asString("unknown"), REST_WHEN_BUSY, null);
            }
            String piece = text(event);
            if (!piece.isEmpty()) {
                text.append(piece);
                said.accept(piece);
            }
            last = event;
        }
        if (last == null) {
            throw new Unavailable("the answer ended before it began", REST_WHEN_BUSY, null);
        }
        return finished(last, text.toString(), started);
    }

    /** The answer's text and token counts; an empty answer is either withheld (final) or a reason to ask another. */
    private static LlmResponse finished(JsonNode last, String text, long started) {
        if (text.isBlank()) {
            String blocked = last.path("promptFeedback").path("blockReason").asString("");
            String ending = last.path("candidates").path(0).path("finishReason").asString("");
            if (!blocked.isEmpty() || WITHHELD.contains(ending)) {
                throw new LlmException("Gemini withheld the answer (" + (blocked.isEmpty() ? ending : blocked) + ")");
            }
            String raw = last.toString();
            log.warn("Gemini sent an empty answer: {}", raw.length() > 600 ? raw.substring(0, 600) + "..." : raw);
            throw new Unavailable("empty answer (" + (ending.isEmpty() ? "no reason given" : ending) + ")",
                    REST_WHEN_BUSY, null);
        }
        JsonNode usage = last.path("usageMetadata");
        int input = usage.path("promptTokenCount").asInt(0);
        int output = usage.path("candidatesTokenCount").asInt(0) + usage.path("thoughtsTokenCount").asInt(0);
        return new LlmResponse(text, input, output, (System.nanoTime() - started) / 1_000_000);
    }

    /** The answer's words, leaving out any of the model's thinking that came back with them. */
    static String text(JsonNode event) {
        StringBuilder text = new StringBuilder();
        for (JsonNode part : event.path("candidates").path(0).path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asString(""));
            }
        }
        return text.toString();
    }

    /** What an error answer means for trying again: another model, the same one without thinking, or not at all. */
    private static RuntimeException failure(int status, String error, boolean thinking) {
        String message = briefly(error);
        if (status == 400 && thinking && error.toLowerCase().contains("thinking")) {
            return new ThinkingRefused();
        }
        if (status == 429) {
            Duration wait = retryDelay(error);
            return wait == null ? new Unavailable(status + " " + message, REST_WHEN_LIMITED, null)
                    : Unavailable.outOfQuota(status + " " + message, wait);
        }
        if (BUSY.contains(status)) {
            return new Unavailable(status + " " + message, REST_WHEN_BUSY, null);
        }
        if (status == 400 || status == 404) {
            // A model this key can't use, or a request only some models accept: the next model may still answer.
            return new Unavailable(status + " " + message, REST_WHEN_MISSING, null);
        }
        // 401/403: the key itself was refused, so no model will answer.
        return new LlmException("Gemini refused the request: " + status + " " + message);
    }

    /**
     * How long Google says to wait before asking again ({@code "retryDelay": "37047s"} when a day's free requests are
     * used up, under a minute for a per-minute limit), or null when it doesn't say.
     */
    static Duration retryDelay(String error) {
        try {
            for (JsonNode detail : JSON.readTree(error).path("error").path("details")) {
                String delay = detail.path("retryDelay").asString("");
                if (delay.endsWith("s")) {
                    long millis = Math.round(Double.parseDouble(delay.substring(0, delay.length() - 1)) * 1000);
                    Duration wait = Duration.ofMillis(millis).plusSeconds(1);
                    return wait.compareTo(LONGEST_QUOTA_WAIT) > 0 ? LONGEST_QUOTA_WAIT : wait;
                }
            }
        } catch (JacksonException | NumberFormatException e) {
            // No usable delay; the usual rest applies.
        }
        return null;
    }

    private static String briefly(String error) {
        try {
            String message = JSON.readTree(error).path("error").path("message").asString("");
            if (!message.isBlank()) {
                return message.length() > 200 ? message.substring(0, 200) + "..." : message;
            }
        } catch (JacksonException e) {
            // Not JSON; use the start of the text.
        }
        String text = error.strip().replaceAll("\\s+", " ");
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    /** Says at startup whether the key works and which models Luna uses, without spending any requests. */
    @EventListener(ApplicationReadyEvent.class)
    void checkKey() {
        if (apiKey.isEmpty()) {
            log.warn("LLM_PROVIDER is gemini but neither GEMINI_API_KEY nor GOOGLE_API_KEY is set; Luna uses her "
                    + "built-in replies until one is");
            return;
        }
        String first = replies.models().getFirst();
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(baseUrl.resolve("v1beta/models/" + first))
                    .header("x-goog-api-key", apiKey).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("Luna thinks with Gemini: replies from {}, notes and summaries from {}",
                        replies.models(), thorough.models());
            } else {
                log.warn("Gemini didn't accept the key or the model {} ({} {}); Luna uses her built-in replies "
                        + "until it does", first, response.statusCode(), briefly(response.body()));
            }
        } catch (IOException e) {
            log.warn("Couldn't reach Gemini to check the key ({}); trying again with the first reply", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Passes streamed text on to whoever is showing it, remembering whether any reached the learner. */
    private static final class Said implements Consumer<String> {

        private final Consumer<String> onDelta;
        private boolean any;

        Said(Consumer<String> onDelta) {
            this.onDelta = onDelta;
        }

        boolean any() {
            return any;
        }

        @Override
        public void accept(String piece) {
            if (onDelta != null) {
                any = true;
                onDelta.accept(piece);
            }
        }
    }

    /** This model can't answer right now; the next one may, and this one rests for {@link #rest()}. */
    static final class Unavailable extends LlmException {

        private final transient Duration rest;
        private final transient Duration waitExactly;
        private final boolean connectionLost;

        Unavailable(String message, Duration rest, Throwable cause) {
            this(message, rest, null, cause, false);
        }

        private Unavailable(String message, Duration rest, Duration waitExactly, Throwable cause,
                boolean connectionLost) {
            super(message, cause);
            this.rest = rest;
            this.waitExactly = waitExactly;
            this.connectionLost = connectionLost;
        }

        /** The connection failed, not the model: worth asking again, and no reason to rest the model. */
        static Unavailable connectionLost(String message, Throwable cause) {
            return new Unavailable(message, Duration.ZERO, null, cause, true);
        }

        /** Out of requests until Google's quota resets, {@code wait} from now. */
        static Unavailable outOfQuota(String message, Duration wait) {
            return new Unavailable(message, Duration.ZERO, wait, null, false);
        }

        Duration rest() {
            return rest;
        }

        /** How long to leave this model alone exactly, when Google said; otherwise null. */
        Duration waitExactly() {
            return waitExactly;
        }

        boolean connectionLost() {
            return connectionLost;
        }
    }

    /** The model doesn't take the thinking setting; ask it again without one. */
    private static final class ThinkingRefused extends RuntimeException {

        ThinkingRefused() {
            super(null, null, false, false);
        }
    }
}
